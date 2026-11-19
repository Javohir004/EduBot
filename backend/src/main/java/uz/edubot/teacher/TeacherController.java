package uz.edubot.teacher;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
import uz.edubot.common.PersonStatus;
import uz.edubot.common.web.PageResponse;
import uz.edubot.common.web.Pages;
import uz.edubot.common.web.Versions;
import uz.edubot.invite.InviteLink;
import uz.edubot.security.CurrentMember;

@RestController
@RequestMapping("/api/v1/teachers")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('TEACHER_MANAGE')")
class TeacherController {

    private final TeacherService teacherService;

    @GetMapping
    PageResponse<TeacherResponse> search(@RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "ACTIVE") PersonStatus status,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "30") int size) {
        return PageResponse.of(teacherService.search(q, status, Pages.of(page, size, Sort.by("fullName"))),
                TeacherResponse::of);
    }

    @GetMapping("/{id}")
    TeacherResponse get(@PathVariable UUID id) {
        return TeacherResponse.of(teacherService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TeacherResponse create(@Valid @RequestBody TeacherRequest request) {
        return TeacherResponse.of(teacherService.create(request.fullName(), request.phone(), request.subjects()));
    }

    @PutMapping("/{id}")
    TeacherResponse update(@PathVariable UUID id, @Valid @RequestBody TeacherRequest request) {
        return TeacherResponse.of(teacherService.update(id, Versions.require(request.version()), request.fullName(), request.phone(),
                request.subjects()));
    }

    @PostMapping("/{id}/archive")
    TeacherResponse archive(@PathVariable UUID id) {
        return TeacherResponse.of(teacherService.archive(id));
    }

    @PostMapping("/{id}/restore")
    TeacherResponse restore(@PathVariable UUID id) {
        return TeacherResponse.of(teacherService.restore(id));
    }

    @PostMapping("/{id}/invite")
    @ResponseStatus(HttpStatus.CREATED)
    InviteLink invite(@AuthenticationPrincipal CurrentMember member, @PathVariable UUID id) {
        return InviteLink.of(teacherService.invite(member, id));
    }

    /** {@code version} is required for updates (optimistic locking) and ignored on create. */
    record TeacherRequest(@NotBlank @Size(max = 200) String fullName,
                          @Size(max = 32) String phone,
                          @Size(max = 300) String subjects,
                          Long version) {
    }

    record TeacherResponse(UUID id, String fullName, String phone, String subjects, PersonStatus status,
                           boolean linked, long version) {

        static TeacherResponse of(Teacher t) {
            return new TeacherResponse(t.getId(), t.getFullName(), t.getPhone(), t.getSubjects(), t.getStatus(),
                    t.getUserId() != null, t.getVersion());
        }
    }
}
