package uz.edubot.billing;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Billing SQL. Every statement carries an explicit center id (native SQL is outside the Hibernate
 * tenant filter). Money is NUMERIC in the database and BigDecimal in Java.
 */
@Repository
@RequiredArgsConstructor
class BillingRepository {

    private final JdbcTemplate jdbc;

    record OpenCharge(UUID id, BigDecimal remaining) {
    }

    record FreePayment(UUID id, BigDecimal free) {
    }

    record ChargeRow(UUID id, UUID groupId, String groupName, LocalDate periodStart, LocalDate periodEnd,
                     BigDecimal amount, BigDecimal paidAmount, LocalDate dueDate, String status) {
    }

    record PaymentRow(UUID id, BigDecimal amount, BigDecimal allocated, String method, Instant paidAt, String note,
                      String status, String voidReason, String receivedByName) {
    }

    record DebtorRow(UUID studentId, String firstName, String lastName, String phone, BigDecimal debt,
                     LocalDate oldestDue) {
    }

    /** Serializes all billing changes of one student (allocation is read-modify-write). */
    boolean lockStudent(UUID centerId, UUID studentId) {
        return !jdbc.queryForList("select id from student where center_id = ? and id = ? for update",
                UUID.class, centerId, studentId).isEmpty();
    }

    /**
     * Creates the month's charge for every enrollment that is active at some point in the month
     * (optionally just one enrollment). Existing charges are kept: re-running is a no-op.
     *
     * @return students who received a new charge
     */
    List<UUID> generateCharges(UUID centerId, LocalDate start, LocalDate end, LocalDate due, UUID enrollmentId) {
        return jdbc.queryForList("""
                        insert into tuition_charge (id, center_id, enrollment_id, student_id, group_id, period_start,
                                                    period_end, amount, paid_amount, due_date, status, created_at, updated_at)
                        select uuidv7(), e.center_id, e.id, e.student_id, e.group_id, ?, ?,
                               coalesce(e.fee_override, g.monthly_fee), 0, ?, 'OPEN', now(), now()
                        from enrollment e
                        join study_group g on g.center_id = e.center_id and g.id = e.group_id
                        where e.center_id = ? and e.joined_on <= ? and (e.left_on is null or e.left_on >= ?)
                          and (?::uuid is null or e.id = ?::uuid)
                        on conflict (enrollment_id, period_start) do nothing
                        returning student_id
                        """, UUID.class, start, end, due, centerId, end, start, enrollmentId, enrollmentId)
                .stream().distinct().toList();
    }

    List<OpenCharge> lockOpenCharges(UUID centerId, UUID studentId) {
        return jdbc.query("""
                        select id, amount - paid_amount from tuition_charge
                        where center_id = ? and student_id = ? and status = 'OPEN' and paid_amount < amount
                        order by period_start, due_date, created_at
                        for update
                        """, (rs, i) -> new OpenCharge(rs.getObject(1, UUID.class), rs.getBigDecimal(2)),
                centerId, studentId);
    }

    List<FreePayment> freePayments(UUID centerId, UUID studentId) {
        return jdbc.query("""
                        select p.id, p.amount - coalesce(sum(a.amount), 0)
                        from payment p left join payment_allocation a on a.payment_id = p.id
                        where p.center_id = ? and p.student_id = ? and p.status = 'ACTIVE'
                        group by p.id, p.amount, p.paid_at, p.created_at
                        having p.amount - coalesce(sum(a.amount), 0) > 0
                        order by p.paid_at, p.created_at
                        """, (rs, i) -> new FreePayment(rs.getObject(1, UUID.class), rs.getBigDecimal(2)),
                centerId, studentId);
    }

    void allocate(UUID centerId, UUID paymentId, UUID chargeId, BigDecimal amount) {
        jdbc.update("""
                insert into payment_allocation (id, center_id, payment_id, charge_id, amount, created_at)
                values (uuidv7(), ?, ?, ?, ?, now())
                on conflict (payment_id, charge_id) do update set amount = payment_allocation.amount + excluded.amount
                """, centerId, paymentId, chargeId, amount);
        jdbc.update("update tuition_charge set paid_amount = paid_amount + ?, updated_at = now() "
                + "where center_id = ? and id = ?", amount, centerId, chargeId);
    }

    /** Removes a payment's allocations and gives the money back to the charges' balances. */
    void releasePayment(UUID centerId, UUID paymentId) {
        jdbc.update("""
                update tuition_charge c set paid_amount = c.paid_amount - a.amount, updated_at = now()
                from payment_allocation a
                where a.charge_id = c.id and a.payment_id = ? and c.center_id = ?
                """, paymentId, centerId);
        jdbc.update("delete from payment_allocation where center_id = ? and payment_id = ?", centerId, paymentId);
    }

    /** Removes a charge's allocations; the payments become free credit again. */
    void releaseCharge(UUID centerId, UUID chargeId) {
        jdbc.update("delete from payment_allocation where center_id = ? and charge_id = ?", centerId, chargeId);
        jdbc.update("update tuition_charge set paid_amount = 0, updated_at = now() where center_id = ? and id = ?",
                centerId, chargeId);
    }

    /** @return the new payment id, or the existing one when the idempotency key was already used */
    UUID insertPayment(UUID centerId, UUID studentId, BigDecimal amount, String method, Instant paidAt,
                       UUID receivedBy, String note, String idempotencyKey) {
        List<UUID> inserted = jdbc.queryForList("""
                insert into payment (id, center_id, student_id, amount, method, paid_at, received_by, note, status,
                                     idempotency_key, created_at)
                values (uuidv7(), ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, now())
                on conflict (center_id, idempotency_key) do nothing
                returning id
                """, UUID.class, centerId, studentId, amount, method, Timestamp.from(paidAt), receivedBy, note,
                idempotencyKey);
        if (!inserted.isEmpty()) {
            return inserted.getFirst();
        }
        return jdbc.queryForObject("select id from payment where center_id = ? and idempotency_key = ?",
                UUID.class, centerId, idempotencyKey);
    }

    Optional<Map<String, Object>> findPayment(UUID centerId, UUID paymentId) {
        return jdbc.queryForList("select id, student_id, amount, status from payment where center_id = ? and id = ?",
                centerId, paymentId).stream().findFirst();
    }

    void markVoided(UUID centerId, UUID paymentId, UUID userId, String reason) {
        jdbc.update("""
                update payment set status = 'VOIDED', voided_at = now(), voided_by = ?, void_reason = ?
                where center_id = ? and id = ?
                """, userId, reason, centerId, paymentId);
    }

    Optional<ChargeRow> findCharge(UUID centerId, UUID chargeId) {
        return charges(centerId, "c.id = ?", chargeId).stream().findFirst();
    }

    List<ChargeRow> chargesOfStudent(UUID centerId, UUID studentId) {
        return charges(centerId, "c.student_id = ? order by c.period_start desc, g.name limit 36", studentId);
    }

    private List<ChargeRow> charges(UUID centerId, String condition, Object arg) {
        return jdbc.query("""
                        select c.id, c.group_id, g.name, c.period_start, c.period_end, c.amount, c.paid_amount,
                               c.due_date, c.status
                        from tuition_charge c join study_group g on g.center_id = c.center_id and g.id = c.group_id
                        where c.center_id = ? and\s""" + condition,
                (rs, i) -> new ChargeRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                        rs.getObject(4, LocalDate.class), rs.getObject(5, LocalDate.class), rs.getBigDecimal(6),
                        rs.getBigDecimal(7), rs.getObject(8, LocalDate.class), rs.getString(9)),
                centerId, arg);
    }

    void updateChargeAmount(UUID centerId, UUID chargeId, BigDecimal amount) {
        jdbc.update("update tuition_charge set amount = ?, updated_at = now() where center_id = ? and id = ?",
                amount, centerId, chargeId);
    }

    void voidCharge(UUID centerId, UUID chargeId) {
        jdbc.update("update tuition_charge set status = 'VOID', updated_at = now() where center_id = ? and id = ?",
                centerId, chargeId);
    }

    UUID studentOfCharge(UUID centerId, UUID chargeId) {
        return jdbc.queryForObject("select student_id from tuition_charge where center_id = ? and id = ?",
                UUID.class, centerId, chargeId);
    }

    List<PaymentRow> paymentsOfStudent(UUID centerId, UUID studentId) {
        return jdbc.query("""
                        select p.id, p.amount, coalesce((select sum(a.amount) from payment_allocation a
                                                         where a.payment_id = p.id), 0),
                               p.method, p.paid_at, p.note, p.status, p.void_reason,
                               trim(u.first_name || ' ' || coalesce(u.last_name, ''))
                        from payment p join app_user u on u.id = p.received_by
                        where p.center_id = ? and p.student_id = ?
                        order by p.paid_at desc limit 50
                        """, (rs, i) -> new PaymentRow(rs.getObject(1, UUID.class), rs.getBigDecimal(2),
                        rs.getBigDecimal(3), rs.getString(4), rs.getTimestamp(5).toInstant(), rs.getString(6),
                        rs.getString(7), rs.getString(8), rs.getString(9)),
                centerId, studentId);
    }

    /** Outstanding amount of charges for months that have started (future months are not debt yet). */
    BigDecimal outstanding(UUID centerId, UUID studentId, LocalDate today) {
        return jdbc.queryForObject("""
                select coalesce(sum(amount - paid_amount), 0) from tuition_charge
                where center_id = ? and student_id = ? and status = 'OPEN' and period_start <= ?
                """, BigDecimal.class, centerId, studentId, today);
    }

    BigDecimal credit(UUID centerId, UUID studentId) {
        return freePayments(centerId, studentId).stream().map(FreePayment::free).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    List<DebtorRow> debtors(UUID centerId, LocalDate today, UUID groupId, int limit, int offset) {
        return jdbc.query("""
                        select s.id, s.first_name, s.last_name, s.phone,
                               sum(c.amount - c.paid_amount) as debt, min(c.due_date)
                        from tuition_charge c join student s on s.center_id = c.center_id and s.id = c.student_id
                        where c.center_id = ? and c.status = 'OPEN' and c.paid_amount < c.amount and c.period_start <= ?
                          and (?::uuid is null or c.group_id = ?::uuid)
                        group by s.id, s.first_name, s.last_name, s.phone
                        order by debt desc, s.last_name
                        limit ? offset ?
                        """, (rs, i) -> new DebtorRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getBigDecimal(5), rs.getObject(6, LocalDate.class)),
                centerId, today, groupId, groupId, limit, offset);
    }

    Map<String, Object> debtTotals(UUID centerId, LocalDate today, UUID groupId) {
        return jdbc.queryForMap("""
                select count(distinct student_id) as students, coalesce(sum(amount - paid_amount), 0) as total
                from tuition_charge
                where center_id = ? and status = 'OPEN' and paid_amount < amount and period_start <= ?
                  and (?::uuid is null or group_id = ?::uuid)
                """, centerId, today, groupId, groupId);
    }

    /** First guardian phone per student, for "call the parent" in the debtor list. */
    Map<UUID, String> guardianPhones(UUID centerId, Collection<UUID> studentIds) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        return jdbc.query("""
                        select distinct on (sg.student_id) sg.student_id, g.phone
                        from student_guardian sg join guardian g on g.center_id = sg.center_id and g.id = sg.guardian_id
                        where sg.center_id = ? and sg.student_id = any(?)
                        order by sg.student_id, sg.created_at
                        """, (rs, i) -> Map.entry(rs.getObject(1, UUID.class), rs.getString(2)),
                        centerId, studentIds.toArray(UUID[]::new))
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    List<UUID> activeCenterIds() {
        return jdbc.queryForList("select id from education_center where status = 'ACTIVE'", UUID.class);
    }
}
