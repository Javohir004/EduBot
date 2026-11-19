package uz.edubot.group;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.edubot.common.error.ApiException;
import uz.edubot.common.time.BusinessTime;
import uz.edubot.common.web.Pages;
import uz.edubot.common.web.Versions;
import uz.edubot.security.CurrentMember;
import uz.edubot.security.Permission;
import uz.edubot.student.StudentArchivedEvent;
import uz.edubot.student.StudentService;
import uz.edubot.subscription.EntitlementKey;
import uz.edubot.subscription.EntitlementService;
import uz.edubot.teacher.TeacherService;
import uz.edubot.tenancy.TenantContext;

/**
 * Groups, schedules and enrollments. Staff with {@code GROUP_WRITE} see every group of the center;
 * a teacher sees only the groups assigned to their own teacher profile.
 */
@Service
@RequiredArgsConstructor
public class GroupService {

    private static final UUID NO_TEACHER = new UUID(0L, 0L);

    private final StudyGroupRepository groups;
    private final GroupScheduleRepository schedules;
    private final EnrollmentRepository enrollments;
    private final TeacherService teacherService;
    private final StudentService studentService;
    private final EntitlementService entitlements;
    private final Clock clock;

    public record Slot(java.time.DayOfWeek day, java.time.LocalTime start, java.time.LocalTime end, String room) {
    }

    // ---- reading (scoped) ----

    @Transactional(readOnly = true)
    public Page<StudyGroup> search(CurrentMember member, String query, GroupStatus status, Pageable pageable) {
        boolean all = member.has(Permission.GROUP_WRITE);
        UUID teacherId = all ? NO_TEACHER : ownTeacherId(member);
        return groups.search(status, Pages.likePattern(query), all, teacherId, pageable);
    }

    /** 404 for groups of another center and for groups a teacher does not teach. */
    @Transactional(readOnly = true)
    public StudyGroup getReadable(CurrentMember member, UUID id) {
        StudyGroup group = get(id);
        if (!member.has(Permission.GROUP_WRITE) && !ownTeacherId(member).equals(group.getTeacherId())) {
            throw groupNotFound();
        }
        return group;
    }

    @Transactional(readOnly = true)
    public List<GroupSchedule> scheduleOf(UUID groupId) {
        return schedules.findByGroup(groupId);
    }

    @Transactional(readOnly = true)
    public List<Enrollment> activeMembers(UUID groupId) {
        return enrollments.findActiveByGroup(groupId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, Long> activeCounts(Collection<UUID> groupIds) {
        Map<UUID, Long> counts = new HashMap<>();
        if (!groupIds.isEmpty()) {
            enrollments.countActiveByGroups(groupIds).forEach(row -> counts.put((UUID) row[0], (Long) row[1]));
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public List<Enrollment> enrollmentsOf(UUID studentId) {
        studentService.get(studentId);
        return enrollments.findByStudent(studentId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, StudyGroup> findByIds(Collection<UUID> ids) {
        Map<UUID, StudyGroup> byId = new HashMap<>();
        if (!ids.isEmpty()) {
            groups.findByIds(ids).forEach(g -> byId.put(g.getId(), g));
        }
        return byId;
    }

    // ---- writing (GROUP_WRITE is checked by the controller) ----

    @Transactional
    public StudyGroup create(GroupData data) {
        validateTeacher(data.teacherId());
        entitlements.requireCapacity(TenantContext.require(), EntitlementKey.MAX_GROUPS,
                () -> groups.countByStatus(GroupStatus.ACTIVE));
        return groups.save(new StudyGroup(data));
    }

    @Transactional
    public StudyGroup update(UUID id, long expectedVersion, GroupData data) {
        StudyGroup group = requireActiveGroup(id);
        Versions.check(group.getVersion(), expectedVersion);
        if (data.teacherId() != null && !data.teacherId().equals(group.getTeacherId())) {
            validateTeacher(data.teacherId());
        }
        if (data.capacity() != null && enrollments.countActiveByGroup(id) > data.capacity()) {
            throw new ApiException(HttpStatus.CONFLICT, "CAPACITY_BELOW_MEMBERS",
                    "Capacity is lower than the number of current students");
        }
        group.update(data);
        return group;
    }

    /** Completes the group and ends all active enrollments today (Tashkent date). */
    @Transactional
    public StudyGroup complete(UUID id) {
        StudyGroup group = requireActiveGroup(id);
        LocalDate today = BusinessTime.today(clock);
        enrollments.findActiveByGroup(id).forEach(e -> e.end(today, "Group completed"));
        group.complete(today);
        return group;
    }

    /** Replaces the weekly schedule as a whole (the UI edits it as one list). */
    @Transactional
    public List<GroupSchedule> replaceSchedule(UUID groupId, List<Slot> slots) {
        requireActiveGroup(groupId);
        long distinct = slots.stream().map(s -> s.day() + "@" + s.start()).distinct().count();
        if (distinct != slots.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_SLOT", "Two lessons start at the same time");
        }
        if (slots.stream().anyMatch(s -> !s.end().isAfter(s.start()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SLOT", "Lesson must end after it starts");
        }
        schedules.deleteByGroup(groupId);
        return schedules.saveAll(slots.stream()
                .map(s -> new GroupSchedule(groupId, s.day(), s.start(), s.end(), blankToNull(s.room())))
                .toList());
    }

    @Transactional
    public Enrollment enroll(UUID groupId, UUID studentId, LocalDate joinedOn, BigDecimal feeOverride) {
        StudyGroup group = groups.findByIdForUpdate(groupId).orElseThrow(GroupService::groupNotFound);
        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw groupCompleted();
        }
        studentService.requireActive(studentId);
        if (enrollments.findActive(studentId, groupId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_ENROLLED", "Student is already in this group");
        }
        if (group.getCapacity() != null && enrollments.countActiveByGroup(groupId) >= group.getCapacity()) {
            throw new ApiException(HttpStatus.CONFLICT, "GROUP_FULL", "The group is full");
        }
        LocalDate joined = joinedOn == null ? BusinessTime.today(clock) : joinedOn;
        return enrollments.save(new Enrollment(studentId, groupId, joined, feeOverride));
    }

    @Transactional
    public Enrollment endEnrollment(UUID enrollmentId, LocalDate leftOn, String reason) {
        Enrollment enrollment = enrollments.findById(enrollmentId)
                .orElseThrow(() -> ApiException.notFound("ENROLLMENT_NOT_FOUND", "Enrollment not found"));
        if (enrollment.getStatus() == EnrollmentStatus.ACTIVE) {
            LocalDate on = leftOn == null ? BusinessTime.today(clock) : leftOn;
            if (on.isBefore(enrollment.getJoinedOn())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE", "Leave date is before join date");
            }
            enrollment.end(on, blankToNull(reason));
        }
        return enrollment;
    }

    @Transactional
    public Enrollment changeFee(UUID enrollmentId, BigDecimal feeOverride) {
        Enrollment enrollment = enrollments.findById(enrollmentId)
                .orElseThrow(() -> ApiException.notFound("ENROLLMENT_NOT_FOUND", "Enrollment not found"));
        enrollment.changeFee(feeOverride);
        return enrollment;
    }

    /** An archived student leaves all groups on the archiving date. */
    @EventListener
    void onStudentArchived(StudentArchivedEvent event) {
        LocalDate today = BusinessTime.today(clock);
        enrollments.findActiveByStudent(event.studentId()).forEach(e -> e.end(today, "Student archived"));
    }

    // ---- helpers ----

    private StudyGroup get(UUID id) {
        return groups.findById(id).orElseThrow(GroupService::groupNotFound);
    }

    private StudyGroup requireActiveGroup(UUID id) {
        StudyGroup group = get(id);
        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw groupCompleted();
        }
        return group;
    }

    private void validateTeacher(UUID teacherId) {
        if (teacherId != null) {
            teacherService.requireActive(teacherId);
        }
    }

    private UUID ownTeacherId(CurrentMember member) {
        return teacherService.findIdByUserId(member.userId()).orElse(NO_TEACHER);
    }

    private static ApiException groupNotFound() {
        return ApiException.notFound("GROUP_NOT_FOUND", "Group not found");
    }

    private static ApiException groupCompleted() {
        return new ApiException(HttpStatus.CONFLICT, "GROUP_COMPLETED", "The group is completed");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
