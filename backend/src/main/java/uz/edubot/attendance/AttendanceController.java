package uz.edubot.attendance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
import uz.edubot.attendance.AttendanceService.History;
import uz.edubot.attendance.AttendanceService.LessonView;
import uz.edubot.attendance.AttendanceService.Mark;
import uz.edubot.attendance.AttendanceService.TodayLesson;
import uz.edubot.security.CurrentMember;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ATTENDANCE_MARK')")
class AttendanceController {

    private final AttendanceService attendanceService;

    @GetMapping("/lessons/today")
    List<TodayLesson> today(@AuthenticationPrincipal CurrentMember member) {
        return attendanceService.today(member);
    }

    @PostMapping("/groups/{groupId}/lessons")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> open(@AuthenticationPrincipal CurrentMember member, @PathVariable UUID groupId,
                           @Valid @RequestBody OpenLessonRequest request) {
        return Map.of("lessonId", attendanceService.openLesson(member, groupId, request.date(), request.startTime()));
    }

    @GetMapping("/lessons/{lessonId}/attendance")
    LessonView lesson(@AuthenticationPrincipal CurrentMember member, @PathVariable UUID lessonId) {
        return attendanceService.lesson(member, lessonId);
    }

    @PutMapping("/lessons/{lessonId}/attendance")
    LessonView mark(@AuthenticationPrincipal CurrentMember member, @PathVariable UUID lessonId,
                    @RequestBody @Size(min = 1, max = 300) List<@Valid MarkRequest> marks) {
        return attendanceService.mark(member, lessonId,
                marks.stream().map(m -> new Mark(m.studentId(), m.status())).toList());
    }

    @GetMapping("/groups/{groupId}/attendance")
    History history(@AuthenticationPrincipal CurrentMember member, @PathVariable UUID groupId,
                    @RequestParam YearMonth month) {
        return attendanceService.history(member, groupId, month);
    }

    record OpenLessonRequest(@NotNull LocalDate date, @NotNull LocalTime startTime) {
    }

    record MarkRequest(@NotNull UUID studentId, @NotNull AttendanceStatus status) {
    }
}
