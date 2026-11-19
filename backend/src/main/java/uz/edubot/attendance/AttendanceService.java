package uz.edubot.attendance;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.edubot.center.CenterService;
import uz.edubot.center.CenterSettings;
import uz.edubot.common.error.ApiException;
import uz.edubot.common.time.BusinessTime;
import uz.edubot.group.Enrollment;
import uz.edubot.group.GroupSchedule;
import uz.edubot.group.GroupService;
import uz.edubot.group.GroupStatus;
import uz.edubot.group.StudyGroup;
import uz.edubot.notification.NotificationService;
import uz.edubot.notification.NotificationType;
import uz.edubot.notification.QuietHours;
import uz.edubot.security.CurrentMember;
import uz.edubot.security.Permission;
import uz.edubot.student.Student;
import uz.edubot.student.StudentService;
import uz.edubot.subscription.EntitlementKey;
import uz.edubot.subscription.EntitlementService;
import uz.edubot.tenancy.TenantContext;

/**
 * Attendance marking. Teachers mark their own groups and may correct marks for
 * {@link #TEACHER_EDIT_DAYS} days; staff with {@code GROUP_WRITE} can always correct.
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    static final int TEACHER_EDIT_DAYS = 2;
    /** Gives the teacher time to fix a wrong mark before the parent is notified. */
    static final Duration ABSENCE_NOTIFY_DELAY = Duration.ofMinutes(15);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final AttendanceRepository repository;
    private final GroupService groupService;
    private final StudentService studentService;
    private final CenterService centerService;
    private final NotificationService notifications;
    private final EntitlementService entitlements;
    private final Clock clock;

    public record TodayLesson(UUID groupId, String groupName, LocalTime startTime, LocalTime endTime, String room,
                              UUID lessonId, int studentCount, int markedCount) {
    }

    public record RosterEntry(UUID studentId, String firstName, String lastName, AttendanceStatus status) {
    }

    public record LessonView(UUID lessonId, UUID groupId, String groupName, LocalDate date, LocalTime startTime,
                             boolean editable, List<RosterEntry> students) {
    }

    public record Mark(UUID studentId, AttendanceStatus status) {
    }

    public record HistoryLesson(UUID lessonId, LocalDate date, LocalTime startTime) {
    }

    public record HistoryRow(UUID studentId, String firstName, String lastName, Map<UUID, AttendanceStatus> marks) {
    }

    public record History(List<HistoryLesson> lessons, List<HistoryRow> students) {
    }

    /** Today's scheduled lessons of the groups the member can see, with marking progress. */
    @Transactional(readOnly = true)
    public List<TodayLesson> today(CurrentMember member) {
        UUID centerId = TenantContext.require();
        LocalDate today = BusinessTime.today(clock);
        Map<UUID, StudyGroup> groups = groupService.activeGroupsFor(member).stream()
                .collect(Collectors.toMap(StudyGroup::getId, g -> g));
        List<GroupSchedule> slots = groupService.slotsOn(groups.keySet(), today.getDayOfWeek());
        Map<String, UUID> lessons = repository.findLessons(centerId, groups.keySet(), today, today).stream()
                .collect(Collectors.toMap(l -> l.groupId() + "@" + l.startTime(), AttendanceRepository.LessonRow::id));
        Map<UUID, Long> marked = repository.findMarks(centerId, lessons.values()).stream()
                .collect(Collectors.groupingBy(AttendanceRepository.MarkRow::lessonId, Collectors.counting()));
        Map<UUID, Long> counts = groupService.activeCounts(groups.keySet());
        return slots.stream()
                .map(s -> {
                    UUID lessonId = lessons.get(s.getGroupId() + "@" + s.getStartTime());
                    return new TodayLesson(s.getGroupId(), groups.get(s.getGroupId()).getName(), s.getStartTime(),
                            s.getEndTime(), s.getRoom(), lessonId,
                            counts.getOrDefault(s.getGroupId(), 0L).intValue(),
                            lessonId == null ? 0 : marked.getOrDefault(lessonId, 0L).intValue());
                })
                .sorted(Comparator.comparing(TodayLesson::startTime))
                .toList();
    }

    @Transactional
    public UUID openLesson(CurrentMember member, UUID groupId, LocalDate date, LocalTime startTime) {
        StudyGroup group = groupService.getReadable(member, groupId);
        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "GROUP_COMPLETED", "The group is completed");
        }
        if (date.isAfter(BusinessTime.today(clock))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LESSON_IN_FUTURE", "Attendance cannot be marked in advance");
        }
        return repository.openLesson(TenantContext.require(), groupId, date, startTime, member.userId());
    }

    @Transactional(readOnly = true)
    public LessonView lesson(CurrentMember member, UUID lessonId) {
        AttendanceRepository.LessonRow lesson = requireLesson(lessonId);
        StudyGroup group = groupService.getReadable(member, lesson.groupId());
        Map<UUID, AttendanceStatus> marks = repository.findMarks(TenantContext.require(), List.of(lessonId)).stream()
                .collect(Collectors.toMap(AttendanceRepository.MarkRow::studentId, AttendanceRepository.MarkRow::status));
        List<RosterEntry> roster = roster(lesson).stream()
                .map(s -> new RosterEntry(s.getId(), s.getFirstName(), s.getLastName(), marks.get(s.getId())))
                .toList();
        return new LessonView(lessonId, group.getId(), group.getName(), lesson.date(), lesson.startTime(),
                canEdit(member, lesson.date()), roster);
    }

    /**
     * Saves marks for any subset of the roster. Re-marking overwrites (upsert), so double taps and
     * concurrent saves never create duplicates. Absences schedule a parent notification; changing
     * the mark before it is sent cancels it.
     */
    @Transactional
    public LessonView mark(CurrentMember member, UUID lessonId, List<Mark> marks) {
        AttendanceRepository.LessonRow lesson = requireLesson(lessonId);
        StudyGroup group = groupService.getReadable(member, lesson.groupId());
        if (!canEdit(member, lesson.date())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EDIT_WINDOW_CLOSED",
                    "Attendance can no longer be changed by a teacher");
        }
        Map<UUID, Student> roster = roster(lesson).stream().collect(Collectors.toMap(Student::getId, s -> s));
        Set<UUID> requested = marks.stream().map(Mark::studentId).collect(Collectors.toSet());
        if (requested.size() != marks.size() || !roster.keySet().containsAll(requested)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_IN_GROUP", "Student is not in this group on that date");
        }

        UUID centerId = TenantContext.require();
        Instant now = clock.instant();
        marks.forEach(m -> repository.upsertMark(centerId, lessonId, m.studentId(), m.status(), member.userId(), now));
        updateAbsenceNotifications(centerId, group, lesson, marks, roster);
        return lesson(member, lessonId);
    }

    @Transactional(readOnly = true)
    public History history(CurrentMember member, UUID groupId, YearMonth month) {
        groupService.getReadable(member, groupId);
        UUID centerId = TenantContext.require();
        List<AttendanceRepository.LessonRow> lessons = repository.findLessons(centerId, List.of(groupId),
                month.atDay(1), month.atEndOfMonth());
        Map<UUID, Map<UUID, AttendanceStatus>> byStudent = new HashMap<>();
        repository.findMarks(centerId, lessons.stream().map(AttendanceRepository.LessonRow::id).toList())
                .forEach(m -> byStudent.computeIfAbsent(m.studentId(), k -> new HashMap<>()).put(m.lessonId(), m.status()));

        // Everyone who was in the group at some point during the month.
        Set<UUID> studentIds = new java.util.HashSet<>(byStudent.keySet());
        for (LocalDate d = month.atDay(1); !d.isAfter(month.atEndOfMonth()); d = d.plusWeeks(1)) {
            groupService.membersOn(groupId, d).forEach(e -> studentIds.add(e.getStudentId()));
        }
        groupService.membersOn(groupId, month.atEndOfMonth()).forEach(e -> studentIds.add(e.getStudentId()));
        Map<UUID, Student> students = studentService.findByIds(studentIds);

        List<HistoryRow> rows = students.values().stream()
                .sorted(Comparator.comparing(Student::getLastName).thenComparing(Student::getFirstName))
                .map(s -> new HistoryRow(s.getId(), s.getFirstName(), s.getLastName(),
                        byStudent.getOrDefault(s.getId(), Map.of())))
                .toList();
        return new History(lessons.stream().map(l -> new HistoryLesson(l.id(), l.date(), l.startTime())).toList(), rows);
    }

    public record StudentMark(LocalDate date, LocalTime startTime, String groupName, AttendanceStatus status) {
    }

    /** The student's marks for the last {@code days} days, newest first. */
    @Transactional(readOnly = true)
    public List<StudentMark> recentForStudent(UUID studentId, int days) {
        return repository.recentMarksOfStudent(TenantContext.require(), studentId,
                        BusinessTime.today(clock).minusDays(days)).stream()
                .map(r -> new StudentMark(r.date(), r.startTime(), r.groupName(), r.status()))
                .toList();
    }

    private void updateAbsenceNotifications(UUID centerId, StudyGroup group, AttendanceRepository.LessonRow lesson,
                                            List<Mark> marks, Map<UUID, Student> roster) {
        List<UUID> absent = marks.stream().filter(m -> m.status() == AttendanceStatus.ABSENT).map(Mark::studentId).toList();
        // Any non-absent mark withdraws a not-yet-sent absence message.
        marks.stream().filter(m -> m.status() != AttendanceStatus.ABSENT)
                .forEach(m -> notifications.cancelPending(absenceKeyPrefix(lesson.id(), m.studentId())));
        if (absent.isEmpty()) {
            return;
        }
        CenterSettings settings = centerService.settings(centerId);
        if (!settings.isAbsenceNotificationsEnabled()
                || !entitlements.isEnabled(centerId, EntitlementKey.FEATURE_NOTIFICATIONS)) {
            return;
        }
        String centerName = centerService.findById(centerId).map(c -> c.getName()).orElse("");
        Instant sendAt = QuietHours.shift(clock.instant().plus(ABSENCE_NOTIFY_DELAY),
                settings.getQuietHoursStart(), settings.getQuietHoursEnd(), BusinessTime.ZONE);
        Map<UUID, List<UUID>> guardians = studentService.guardianUserIds(absent);
        for (UUID studentId : absent) {
            Student student = roster.get(studentId);
            Map<String, Object> payload = Map.of(
                    "studentName", student.getFirstName() + " " + student.getLastName(),
                    "groupName", group.getName(),
                    "date", lesson.date().format(DATE),
                    "time", lesson.startTime().format(TIME),
                    "centerName", centerName);
            for (UUID guardianUserId : guardians.getOrDefault(studentId, List.of())) {
                notifications.enqueue(centerId, guardianUserId, NotificationType.ABSENCE, payload,
                        absenceKeyPrefix(lesson.id(), studentId) + guardianUserId, sendAt);
            }
        }
    }

    private static String absenceKeyPrefix(UUID lessonId, UUID studentId) {
        return "ABSENCE:" + lessonId + ":" + studentId + ":";
    }

    private List<Student> roster(AttendanceRepository.LessonRow lesson) {
        Collection<UUID> ids = groupService.membersOn(lesson.groupId(), lesson.date()).stream()
                .map(Enrollment::getStudentId).distinct().toList();
        return studentService.findByIds(ids).values().stream()
                .sorted(Comparator.comparing(Student::getLastName).thenComparing(Student::getFirstName))
                .toList();
    }

    private boolean canEdit(CurrentMember member, LocalDate lessonDate) {
        if (member.has(Permission.GROUP_WRITE)) {
            return true;
        }
        return member.has(Permission.ATTENDANCE_MARK)
                && !lessonDate.isBefore(BusinessTime.today(clock).minusDays(TEACHER_EDIT_DAYS));
    }

    private AttendanceRepository.LessonRow requireLesson(UUID lessonId) {
        return repository.findLesson(TenantContext.require(), lessonId)
                .orElseThrow(() -> ApiException.notFound("LESSON_NOT_FOUND", "Lesson not found"));
    }
}
