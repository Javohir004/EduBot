package uz.edubot.group;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.edubot.common.web.PageResponse;
import uz.edubot.common.web.Pages;
import uz.edubot.common.web.Versions;
import uz.edubot.group.GroupService.Slot;
import uz.edubot.security.CurrentMember;
import uz.edubot.student.Student;
import uz.edubot.student.StudentService;
import uz.edubot.teacher.Teacher;
import uz.edubot.teacher.TeacherService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class GroupController {

    private final GroupService groupService;
    private final TeacherService teacherService;
    private final StudentService studentService;

    @GetMapping("/groups")
    @PreAuthorize("hasAuthority('GROUP_READ')")
    PageResponse<GroupSummary> search(@AuthenticationPrincipal CurrentMember member,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(defaultValue = "ACTIVE") GroupStatus status,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "30") int size) {
        Page<StudyGroup> result = groupService.search(member, q, status, Pages.of(page, size, Sort.by("name")));
        List<StudyGroup> items = result.getContent();
        Map<UUID, Teacher> teachers = teacherService.findByIds(
                items.stream().map(StudyGroup::getTeacherId).filter(java.util.Objects::nonNull).distinct().toList());
        Map<UUID, Long> counts = groupService.activeCounts(items.stream().map(StudyGroup::getId).toList());
        return PageResponse.of(result, g -> GroupSummary.of(g, teacherName(teachers, g), counts.getOrDefault(g.getId(), 0L)));
    }

    @GetMapping("/groups/{id}")
    @PreAuthorize("hasAuthority('GROUP_READ')")
    GroupDetails get(@AuthenticationPrincipal CurrentMember member, @PathVariable UUID id) {
        return details(groupService.getReadable(member, id));
    }

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('GROUP_WRITE')")
    GroupDetails create(@Valid @RequestBody GroupRequest request) {
        return details(groupService.create(request.toData()));
    }

    @PutMapping("/groups/{id}")
    @PreAuthorize("hasAuthority('GROUP_WRITE')")
    GroupDetails update(@PathVariable UUID id, @Valid @RequestBody GroupRequest request) {
        return details(groupService.update(id, Versions.require(request.version()), request.toData()));
    }

    @PostMapping("/groups/{id}/complete")
    @PreAuthorize("hasAuthority('GROUP_WRITE')")
    GroupDetails complete(@PathVariable UUID id) {
        return details(groupService.complete(id));
    }

    @PutMapping("/groups/{id}/schedule")
    @PreAuthorize("hasAuthority('GROUP_WRITE')")
    List<SlotDto> replaceSchedule(@PathVariable UUID id, @RequestBody @Size(max = 14) List<@Valid SlotDto> slots) {
        return groupService.replaceSchedule(id, slots.stream().map(SlotDto::toSlot).toList()).stream()
                .map(SlotDto::of).toList();
    }

    @GetMapping("/groups/{id}/members")
    @PreAuthorize("hasAuthority('GROUP_READ')")
    List<MemberDto> members(@AuthenticationPrincipal CurrentMember member, @PathVariable UUID id) {
        groupService.getReadable(member, id);
        List<Enrollment> active = groupService.activeMembers(id);
        Map<UUID, Student> students = studentService.findByIds(active.stream().map(Enrollment::getStudentId).toList());
        return active.stream()
                .filter(e -> students.containsKey(e.getStudentId()))
                .map(e -> MemberDto.of(e, students.get(e.getStudentId())))
                .sorted(java.util.Comparator.comparing(MemberDto::lastName).thenComparing(MemberDto::firstName))
                .toList();
    }

    @PostMapping("/groups/{id}/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('GROUP_WRITE')")
    EnrollmentDto enroll(@PathVariable UUID id, @Valid @RequestBody EnrollRequest request) {
        return EnrollmentDto.of(groupService.enroll(id, request.studentId(), request.joinedOn(), request.feeOverride()),
                null);
    }

    @PostMapping("/enrollments/{id}/end")
    @PreAuthorize("hasAuthority('GROUP_WRITE')")
    EnrollmentDto end(@PathVariable UUID id, @Valid @RequestBody(required = false) EndRequest request) {
        EndRequest body = request == null ? new EndRequest(null, null) : request;
        return EnrollmentDto.of(groupService.endEnrollment(id, body.leftOn(), body.reason()), null);
    }

    @PutMapping("/enrollments/{id}/fee")
    @PreAuthorize("hasAuthority('GROUP_WRITE')")
    EnrollmentDto changeFee(@PathVariable UUID id, @Valid @RequestBody FeeRequest request) {
        return EnrollmentDto.of(groupService.changeFee(id, request.feeOverride()), null);
    }

    @GetMapping("/students/{id}/enrollments")
    @PreAuthorize("hasAuthority('STUDENT_READ')")
    List<EnrollmentDto> studentEnrollments(@PathVariable UUID id) {
        List<Enrollment> list = groupService.enrollmentsOf(id);
        Map<UUID, StudyGroup> groups = groupService.findByIds(list.stream().map(Enrollment::getGroupId).distinct().toList());
        return list.stream().map(e -> EnrollmentDto.of(e, groups.get(e.getGroupId()))).toList();
    }

    private GroupDetails details(StudyGroup group) {
        Map<UUID, Teacher> teachers = group.getTeacherId() == null ? Map.of()
                : teacherService.findByIds(List.of(group.getTeacherId()));
        long count = groupService.activeCounts(List.of(group.getId())).getOrDefault(group.getId(), 0L);
        return GroupDetails.of(group, teacherName(teachers, group), count,
                groupService.scheduleOf(group.getId()).stream().map(SlotDto::of).toList());
    }

    private static String teacherName(Map<UUID, Teacher> teachers, StudyGroup group) {
        Teacher teacher = group.getTeacherId() == null ? null : teachers.get(group.getTeacherId());
        return teacher == null ? null : teacher.getFullName();
    }

    // ---- DTOs ----

    record GroupRequest(@NotBlank @Size(max = 120) String name,
                        @Size(max = 120) String subject,
                        UUID teacherId,
                        @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 2) BigDecimal monthlyFee,
                        @NotNull LocalDate startDate,
                        LocalDate endDate,
                        @Positive Integer capacity,
                        Long version) {

        GroupData toData() {
            return new GroupData(name.strip(), subject == null || subject.isBlank() ? null : subject.strip(),
                    teacherId, monthlyFee, startDate, endDate, capacity);
        }
    }

    record SlotDto(@NotNull DayOfWeek day, @NotNull LocalTime startTime, @NotNull LocalTime endTime,
                   @Size(max = 60) String room) {

        Slot toSlot() {
            return new Slot(day, startTime, endTime, room);
        }

        static SlotDto of(GroupSchedule s) {
            return new SlotDto(DayOfWeek.of(s.getDayOfWeek()), s.getStartTime(), s.getEndTime(), s.getRoom());
        }
    }

    record GroupSummary(UUID id, String name, String subject, String teacherName, BigDecimal monthlyFee,
                        GroupStatus status, long studentCount, Integer capacity) {

        static GroupSummary of(StudyGroup g, String teacherName, long count) {
            return new GroupSummary(g.getId(), g.getName(), g.getSubject(), teacherName, g.getMonthlyFee(),
                    g.getStatus(), count, g.getCapacity());
        }
    }

    record GroupDetails(UUID id, String name, String subject, UUID teacherId, String teacherName,
                        BigDecimal monthlyFee, LocalDate startDate, LocalDate endDate, Integer capacity,
                        GroupStatus status, long studentCount, long version, List<SlotDto> schedule) {

        static GroupDetails of(StudyGroup g, String teacherName, long count, List<SlotDto> schedule) {
            return new GroupDetails(g.getId(), g.getName(), g.getSubject(), g.getTeacherId(), teacherName,
                    g.getMonthlyFee(), g.getStartDate(), g.getEndDate(), g.getCapacity(), g.getStatus(), count,
                    g.getVersion(), schedule);
        }
    }

    record MemberDto(UUID enrollmentId, UUID studentId, String firstName, String lastName, String phone,
                     LocalDate joinedOn, BigDecimal feeOverride) {

        static MemberDto of(Enrollment e, Student s) {
            return new MemberDto(e.getId(), s.getId(), s.getFirstName(), s.getLastName(), s.getPhone(),
                    e.getJoinedOn(), e.getFeeOverride());
        }
    }

    record EnrollmentDto(UUID id, UUID studentId, UUID groupId, String groupName, LocalDate joinedOn,
                         LocalDate leftOn, EnrollmentStatus status, BigDecimal feeOverride) {

        static EnrollmentDto of(Enrollment e, StudyGroup group) {
            return new EnrollmentDto(e.getId(), e.getStudentId(), e.getGroupId(),
                    group == null ? null : group.getName(), e.getJoinedOn(), e.getLeftOn(), e.getStatus(),
                    e.getFeeOverride());
        }
    }

    record EnrollRequest(@NotNull UUID studentId, LocalDate joinedOn,
                         @PositiveOrZero @Digits(integer = 13, fraction = 2) BigDecimal feeOverride) {
    }

    record EndRequest(LocalDate leftOn, @Size(max = 300) String reason) {
    }

    record FeeRequest(@PositiveOrZero @Digits(integer = 13, fraction = 2) BigDecimal feeOverride) {
    }
}
