package uz.edubot.dashboard;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.edubot.common.time.BusinessTime;
import uz.edubot.security.CurrentMember;
import uz.edubot.security.Permission;
import uz.edubot.tenancy.TenantContext;

/**
 * Read-only aggregates for the home screen. Plain SQL with an explicit center id; money figures
 * are included only for roles that may see them (revenue: owner; debt: payment readers).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class DashboardController {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @GetMapping("/dashboard/summary")
    @PreAuthorize("hasAuthority('STUDENT_READ')")
    @Transactional(readOnly = true)
    Summary summary(@AuthenticationPrincipal CurrentMember member) {
        UUID c = TenantContext.require();
        LocalDate today = BusinessTime.today(clock);
        YearMonth month = YearMonth.from(today);
        Instant monthStart = month.atDay(1).atStartOfDay(BusinessTime.ZONE).toInstant();

        Map<String, Object> counts = jdbc.queryForMap("""
                select (select count(*) from student where center_id = ? and status = 'ACTIVE') as students,
                       (select count(*) from student where center_id = ? and created_at >= ?) as new_students,
                       (select count(*) from study_group where center_id = ? and status = 'ACTIVE') as groups,
                       (select count(*) from teacher where center_id = ? and status = 'ACTIVE') as teachers
                """, c, c, Timestamp.from(monthStart), c, c);
        Map<String, Object> attendance = jdbc.queryForMap("""
                select count(*) as marked,
                       count(*) filter (where a.status in ('PRESENT', 'LATE')) as present,
                       count(*) filter (where a.status = 'ABSENT') as absent
                from attendance a join lesson l on l.center_id = a.center_id and l.id = a.lesson_id
                where a.center_id = ? and l.lesson_date = ?
                """, c, today);

        BigDecimal revenue = null;
        if (member.has(Permission.REVENUE_READ)) {
            revenue = jdbc.queryForObject("""
                    select coalesce(sum(amount), 0) from payment
                    where center_id = ? and status = 'ACTIVE' and paid_at >= ?
                    """, BigDecimal.class, c, Timestamp.from(monthStart));
        }
        BigDecimal debt = null;
        Long debtors = null;
        if (member.has(Permission.PAYMENT_READ)) {
            Map<String, Object> d = jdbc.queryForMap("""
                    select coalesce(sum(amount - paid_amount), 0) as total, count(distinct student_id) as students
                    from tuition_charge
                    where center_id = ? and status = 'OPEN' and paid_amount < amount and period_start <= ?
                    """, c, today);
            debt = (BigDecimal) d.get("total");
            debtors = ((Number) d.get("students")).longValue();
        }
        return new Summary(num(counts, "students"), num(counts, "new_students"), num(counts, "groups"),
                num(counts, "teachers"), num(attendance, "marked"), num(attendance, "present"),
                num(attendance, "absent"), revenue, debt, debtors);
    }

    /** Latest events: new students, payments, voids. */
    @GetMapping("/activity")
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    @Transactional(readOnly = true)
    List<Activity> activity() {
        UUID c = TenantContext.require();
        return jdbc.query("""
                select * from (
                    select 'STUDENT_ADDED' as type, s.created_at as at, s.id as student_id,
                           s.first_name || ' ' || s.last_name as title, null::numeric as amount
                    from student s where s.center_id = ?
                    union all
                    select 'PAYMENT_RECEIVED', p.created_at, p.student_id, s.first_name || ' ' || s.last_name, p.amount
                    from payment p join student s on s.center_id = p.center_id and s.id = p.student_id
                    where p.center_id = ?
                    union all
                    select 'PAYMENT_VOIDED', p.voided_at, p.student_id, s.first_name || ' ' || s.last_name, p.amount
                    from payment p join student s on s.center_id = p.center_id and s.id = p.student_id
                    where p.center_id = ? and p.status = 'VOIDED'
                ) events
                order by at desc
                limit 20
                """, (rs, i) -> new Activity(rs.getString(1), rs.getTimestamp(2).toInstant(),
                rs.getObject(3, UUID.class), rs.getString(4), rs.getBigDecimal(5)), c, c, c);
    }

    private static long num(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    /** Money fields are {@code null} when the caller may not see them. */
    record Summary(long activeStudents, long newStudentsThisMonth, long activeGroups, long activeTeachers,
                   long attendanceMarkedToday, long presentToday, long absentToday, BigDecimal revenueThisMonth,
                   BigDecimal outstandingDebt, Long debtorCount) {
    }

    record Activity(String type, Instant at, UUID studentId, String title, BigDecimal amount) {
    }
}
