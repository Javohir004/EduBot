package uz.edubot.billing;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.edubot.audit.AuditService;
import uz.edubot.billing.BillingRepository.ChargeRow;
import uz.edubot.billing.BillingRepository.DebtorRow;
import uz.edubot.billing.BillingRepository.FreePayment;
import uz.edubot.billing.BillingRepository.OpenCharge;
import uz.edubot.billing.BillingRepository.PaymentRow;
import uz.edubot.billing.BillingRepository.OverdueRow;
import uz.edubot.center.CenterService;
import uz.edubot.center.CenterSettings;
import uz.edubot.notification.NotificationService;
import uz.edubot.notification.NotificationType;
import uz.edubot.notification.QuietHours;
import uz.edubot.student.StudentService;
import uz.edubot.subscription.EntitlementKey;
import uz.edubot.subscription.EntitlementService;
import uz.edubot.common.error.ApiException;
import uz.edubot.common.time.BusinessTime;
import uz.edubot.group.EnrollmentStartedEvent;
import uz.edubot.security.CurrentMember;
import uz.edubot.tenancy.TenantContext;

/**
 * Tuition billing.
 * <ul>
 *   <li>Each enrollment is charged once per calendar month it is active in (full month, the
 *       amount can be corrected).</li>
 *   <li>Payments are allocated to the oldest open charges first (FIFO); money left over is credit
 *       that pays future charges automatically.</li>
 *   <li>Nothing is deleted: a wrong payment is voided with a reason and an audit record.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    /** Backdated enrollments are charged for at most this many past months automatically. */
    static final int MAX_BACKFILL_MONTHS = 12;

    private final BillingRepository repository;
    private final CenterService centerService;
    private final AuditService audit;
    private final StudentService studentService;
    private final NotificationService notifications;
    private final EntitlementService entitlements;
    private final Clock clock;

    public record Balance(BigDecimal debt, BigDecimal credit, List<ChargeRow> charges, List<PaymentRow> payments) {
    }

    public record Debtor(DebtorRow row, String guardianPhone) {
    }

    public record Debtors(long students, BigDecimal total, List<Debtor> items) {
    }

    // ---- charges ----

    /** Idempotent: creates the month's missing charges for the current center. */
    @Transactional
    public int generateForMonth(YearMonth month) {
        UUID centerId = TenantContext.require();
        List<UUID> students = repository.generateCharges(centerId, month.atDay(1), month.atEndOfMonth(),
                dueDate(centerId, month), null);
        students.forEach(studentId -> allocateCredit(centerId, studentId));
        return students.size();
    }

    /** A new enrollment is billed right away for its first month (and missed months if backdated). */
    @EventListener
    void onEnrollmentStarted(EnrollmentStartedEvent event) {
        UUID centerId = TenantContext.require();
        YearMonth current = YearMonth.from(BusinessTime.today(clock));
        YearMonth month = YearMonth.from(event.joinedOn());
        if (month.isBefore(current.minusMonths(MAX_BACKFILL_MONTHS - 1))) {
            month = current.minusMonths(MAX_BACKFILL_MONTHS - 1);
        }
        boolean charged = false;
        for (; !month.isAfter(current); month = month.plusMonths(1)) {
            charged |= !repository.generateCharges(centerId, month.atDay(1), month.atEndOfMonth(),
                    dueDate(centerId, month), event.enrollmentId()).isEmpty();
        }
        if (charged) {
            repository.lockStudent(centerId, event.studentId());
            allocateCredit(centerId, event.studentId());
        }
    }

    @Transactional
    public ChargeRow changeChargeAmount(CurrentMember member, UUID chargeId, BigDecimal amount, String reason) {
        UUID centerId = TenantContext.require();
        ChargeRow charge = lockedCharge(centerId, chargeId);
        if (amount.compareTo(charge.paidAmount()) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "AMOUNT_BELOW_PAID",
                    "The new amount is lower than what is already paid");
        }
        repository.updateChargeAmount(centerId, chargeId, amount);
        audit.record(centerId, member.userId(), "CHARGE_AMOUNT_CHANGED", "CHARGE", chargeId,
                Map.of("from", charge.amount(), "to", amount, "reason", reason));
        allocateCredit(centerId, repository.studentOfCharge(centerId, chargeId));
        return repository.findCharge(centerId, chargeId).orElseThrow();
    }

    /** Cancels a charge (e.g. created by mistake). Money already applied to it becomes credit again. */
    @Transactional
    public ChargeRow voidCharge(CurrentMember member, UUID chargeId, String reason) {
        UUID centerId = TenantContext.require();
        ChargeRow charge = lockedCharge(centerId, chargeId);
        repository.releaseCharge(centerId, chargeId);
        repository.voidCharge(centerId, chargeId);
        audit.record(centerId, member.userId(), "CHARGE_VOIDED", "CHARGE", chargeId,
                Map.of("amount", charge.amount(), "paid", charge.paidAmount(), "reason", reason));
        allocateCredit(centerId, repository.studentOfCharge(centerId, chargeId));
        return repository.findCharge(centerId, chargeId).orElseThrow();
    }

    // ---- payments ----

    /**
     * Records a manual payment. The idempotency key (sent by the client per form) makes retries safe.
     *
     * @param chargeId optional charge to pay first; the rest goes FIFO
     */
    @Transactional
    public UUID recordPayment(CurrentMember member, UUID studentId, BigDecimal amount, String method, String note,
                              UUID chargeId, String idempotencyKey) {
        UUID centerId = TenantContext.require();
        if (!repository.lockStudent(centerId, studentId)) {
            throw ApiException.notFound("STUDENT_NOT_FOUND", "Student not found");
        }
        if (chargeId != null) {
            ChargeRow charge = repository.findCharge(centerId, chargeId)
                    .orElseThrow(() -> ApiException.notFound("CHARGE_NOT_FOUND", "Charge not found"));
            if (!studentId.equals(repository.studentOfCharge(centerId, chargeId)) || !"OPEN".equals(charge.status())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CHARGE", "The charge does not belong to this student");
            }
        }
        Instant now = clock.instant();
        UUID paymentId = repository.insertPayment(centerId, studentId, amount, method, now, member.userId(),
                note, idempotencyKey);
        allocate(centerId, studentId, chargeId);
        log.info("Payment recorded id={} studentId={}", paymentId, studentId);
        return paymentId;
    }

    @Transactional
    public void voidPayment(CurrentMember member, UUID paymentId, String reason) {
        UUID centerId = TenantContext.require();
        Map<String, Object> payment = repository.findPayment(centerId, paymentId)
                .orElseThrow(() -> ApiException.notFound("PAYMENT_NOT_FOUND", "Payment not found"));
        UUID studentId = (UUID) payment.get("student_id");
        repository.lockStudent(centerId, studentId);
        if (!"ACTIVE".equals(payment.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_ALREADY_VOIDED", "The payment is already voided");
        }
        repository.releasePayment(centerId, paymentId);
        repository.markVoided(centerId, paymentId, member.userId(), reason);
        audit.record(centerId, member.userId(), "PAYMENT_VOIDED", "PAYMENT", paymentId,
                Map.of("amount", payment.get("amount"), "studentId", studentId, "reason", reason));
        // Other payments' credit may now cover the charges this payment was paying.
        allocateCredit(centerId, studentId);
    }

    // ---- reading ----

    @Transactional(readOnly = true)
    public Balance balance(UUID studentId) {
        UUID centerId = TenantContext.require();
        LocalDate today = BusinessTime.today(clock);
        return new Balance(repository.outstanding(centerId, studentId, today), repository.credit(centerId, studentId),
                repository.chargesOfStudent(centerId, studentId), repository.paymentsOfStudent(centerId, studentId));
    }

    @Transactional(readOnly = true)
    public Debtors debtors(UUID groupId, int page, int size) {
        UUID centerId = TenantContext.require();
        LocalDate today = BusinessTime.today(clock);
        List<DebtorRow> rows = repository.debtors(centerId, today, groupId, size, page * size);
        Map<UUID, String> phones = repository.guardianPhones(centerId, rows.stream().map(DebtorRow::studentId).toList());
        Map<String, Object> totals = repository.debtTotals(centerId, today, groupId);
        return new Debtors(((Number) totals.get("students")).longValue(), (BigDecimal) totals.get("total"),
                rows.stream().map(r -> new Debtor(r, phones.get(r.studentId()))).toList());
    }

    // ---- reminders ----

    /**
     * Weekly reminder to parents of students with overdue debt. The dedup key contains the ISO week,
     * so a daily job sends at most one reminder per student and parent per week.
     *
     * @return number of reminders queued
     */
    @Transactional
    public int queueWeeklyReminders() {
        UUID centerId = TenantContext.require();
        if (!centerService.settings(centerId).isPaymentRemindersEnabled()
                || !entitlements.isEnabled(centerId, EntitlementKey.FEATURE_NOTIFICATIONS)) {
            return 0;
        }
        LocalDate today = BusinessTime.today(clock);
        String week = today.get(IsoFields.WEEK_BASED_YEAR) + "W" + today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return queueReminders(centerId, repository.overdue(centerId, today, null), "PAYMENT_REMINDER:%s:%s:" + week);
    }

    /** Reminder sent from the debtors list now; at most one per student and parent per day. */
    @Transactional
    public int remindNow(UUID studentId) {
        UUID centerId = TenantContext.require();
        if (!entitlements.isEnabled(centerId, EntitlementKey.FEATURE_NOTIFICATIONS)) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "FEATURE_NOT_IN_PLAN", "Notifications are not in the plan");
        }
        LocalDate today = BusinessTime.today(clock);
        // Manual reminders also cover debt that is due today.
        List<OverdueRow> rows = repository.overdue(centerId, today.plusDays(1), studentId);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "NO_DEBT", "The student has no overdue debt");
        }
        int queued = queueReminders(centerId, rows, "PAYMENT_REMINDER_NOW:%s:%s:" + today);
        if (queued == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "NO_LINKED_PARENT", "No parent of this student uses the app");
        }
        return queued;
    }

    private int queueReminders(UUID centerId, List<OverdueRow> rows, String keyPattern) {
        if (rows.isEmpty()) {
            return 0;
        }
        String centerName = centerService.findById(centerId).map(c -> c.getName()).orElse("");
        Map<UUID, List<UUID>> parents = studentService.guardianUserIds(rows.stream().map(OverdueRow::studentId).toList());
        CenterSettings settings = centerService.settings(centerId);
        Instant sendAt = QuietHours.shift(clock.instant(), settings.getQuietHoursStart(), settings.getQuietHoursEnd(),
                BusinessTime.ZONE);
        int queued = 0;
        for (OverdueRow row : rows) {
            Map<String, Object> payload = Map.of("studentName", row.studentName(),
                    "amount", formatMoney(row.debt()), "centerName", centerName);
            for (UUID parent : parents.getOrDefault(row.studentId(), List.of())) {
                notifications.enqueue(centerId, parent, NotificationType.PAYMENT_REMINDER, payload,
                        keyPattern.formatted(row.studentId(), parent), sendAt);
                queued++;
            }
        }
        return queued;
    }

    /** 1200000 -> "1 200 000" */
    static String formatMoney(BigDecimal amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator(' ');
        return new DecimalFormat("#,##0", symbols).format(amount);
    }

    // ---- allocation ----

    private void allocateCredit(UUID centerId, UUID studentId) {
        allocate(centerId, studentId, null);
    }

    /** FIFO over open charges (the preferred charge first); caller holds the student lock. */
    private void allocate(UUID centerId, UUID studentId, UUID preferredChargeId) {
        List<OpenCharge> charges = new ArrayList<>(repository.lockOpenCharges(centerId, studentId));
        if (preferredChargeId != null) {
            charges.sort((a, b) -> a.id().equals(preferredChargeId) ? -1 : b.id().equals(preferredChargeId) ? 1 : 0);
        }
        List<FreePayment> payments = repository.freePayments(centerId, studentId);
        int c = 0;
        int p = 0;
        BigDecimal chargeLeft = charges.isEmpty() ? BigDecimal.ZERO : charges.getFirst().remaining();
        BigDecimal paymentLeft = payments.isEmpty() ? BigDecimal.ZERO : payments.getFirst().free();
        while (c < charges.size() && p < payments.size()) {
            BigDecimal part = chargeLeft.min(paymentLeft);
            repository.allocate(centerId, payments.get(p).id(), charges.get(c).id(), part);
            chargeLeft = chargeLeft.subtract(part);
            paymentLeft = paymentLeft.subtract(part);
            if (chargeLeft.signum() == 0 && ++c < charges.size()) {
                chargeLeft = charges.get(c).remaining();
            }
            if (paymentLeft.signum() == 0 && ++p < payments.size()) {
                paymentLeft = payments.get(p).free();
            }
        }
    }

    private ChargeRow lockedCharge(UUID centerId, UUID chargeId) {
        ChargeRow charge = repository.findCharge(centerId, chargeId)
                .orElseThrow(() -> ApiException.notFound("CHARGE_NOT_FOUND", "Charge not found"));
        repository.lockStudent(centerId, repository.studentOfCharge(centerId, chargeId));
        if (!"OPEN".equals(charge.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "CHARGE_VOIDED", "The charge is voided");
        }
        return repository.findCharge(centerId, chargeId).orElseThrow();
    }

    private LocalDate dueDate(UUID centerId, YearMonth month) {
        int day = Math.min(centerService.settings(centerId).getPaymentDueDay(), month.lengthOfMonth());
        return month.atDay(day);
    }
}
