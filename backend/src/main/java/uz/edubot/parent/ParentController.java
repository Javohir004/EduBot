package uz.edubot.parent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.edubot.attendance.AttendanceService;
import uz.edubot.attendance.AttendanceStatus;
import uz.edubot.billing.BillingService;
import uz.edubot.common.error.ApiException;
import uz.edubot.group.Enrollment;
import uz.edubot.group.EnrollmentStatus;
import uz.edubot.group.GroupService;
import uz.edubot.group.StudyGroup;
import uz.edubot.security.CurrentMember;
import uz.edubot.student.Student;
import uz.edubot.student.StudentService;
import uz.edubot.subscription.EntitlementKey;
import uz.edubot.subscription.EntitlementService;
import uz.edubot.tenancy.TenantContext;

/**
 * Read-only view for parents. A parent sees only students linked to a guardian record that
 * belongs to their own Telegram account; any other id is 404.
 */
@RestController
@RequestMapping("/api/v1/parent")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('CHILD_READ')")
class ParentController {

    static final int ATTENDANCE_DAYS = 30;

    private final StudentService studentService;
    private final GroupService groupService;
    private final AttendanceService attendanceService;
    private final BillingService billingService;
    private final EntitlementService entitlements;

    @GetMapping("/children")
    @Transactional(readOnly = true)
    List<ChildSummary> children(@AuthenticationPrincipal CurrentMember member) {
        requirePortal();
        return studentService.childrenOfUser(member.userId()).stream()
                .map(s -> new ChildSummary(s.getId(), s.getFirstName(), s.getLastName(),
                        billingService.balance(s.getId()).debt()))
                .toList();
    }

    @GetMapping("/children/{studentId}")
    @Transactional(readOnly = true)
    ChildDetails child(@AuthenticationPrincipal CurrentMember member, @PathVariable UUID studentId) {
        requirePortal();
        Student child = studentService.childrenOfUser(member.userId()).stream()
                .filter(s -> s.getId().equals(studentId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("CHILD_NOT_FOUND", "Child not found"));

        List<Enrollment> active = groupService.enrollmentsOf(child.getId()).stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE).toList();
        Map<UUID, StudyGroup> groups = groupService.findByIds(active.stream().map(Enrollment::getGroupId).toList());
        List<String> groupNames = active.stream().map(e -> groups.get(e.getGroupId()))
                .filter(java.util.Objects::nonNull).map(StudyGroup::getName).toList();

        List<Mark> attendance = attendanceService.recentForStudent(child.getId(), ATTENDANCE_DAYS).stream()
                .map(m -> new Mark(m.date(), m.startTime(), m.groupName(), m.status())).toList();
        BillingService.Balance balance = billingService.balance(child.getId());
        List<Charge> charges = balance.charges().stream().limit(6)
                .map(c -> new Charge(c.periodStart(), c.groupName(), c.amount(), c.paidAmount(), c.status()))
                .toList();
        return new ChildDetails(child.getId(), child.getFirstName(), child.getLastName(), groupNames, attendance,
                balance.debt(), balance.credit(), charges);
    }

    private void requirePortal() {
        if (!entitlements.isEnabled(TenantContext.require(), EntitlementKey.FEATURE_PARENT_PORTAL)) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "FEATURE_NOT_IN_PLAN",
                    "The parent portal is not included in the center's plan");
        }
    }

    record ChildSummary(UUID studentId, String firstName, String lastName, BigDecimal debt) {
    }

    record Mark(LocalDate date, LocalTime startTime, String groupName, AttendanceStatus status) {
    }

    record Charge(LocalDate periodStart, String groupName, BigDecimal amount, BigDecimal paidAmount, String status) {
    }

    record ChildDetails(UUID studentId, String firstName, String lastName, List<String> groups, List<Mark> attendance,
                        BigDecimal debt, BigDecimal credit, List<Charge> charges) {
    }
}
