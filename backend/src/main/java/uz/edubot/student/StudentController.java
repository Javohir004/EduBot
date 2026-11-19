package uz.edubot.student;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import uz.edubot.student.StudentService.GuardianInput;
import uz.edubot.student.StudentService.LinkedGuardian;

@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
class StudentController {

    private final StudentService studentService;

    @GetMapping
    @PreAuthorize("hasAuthority('STUDENT_READ')")
    PageResponse<StudentSummary> search(@RequestParam(required = false) String q,
                                        @RequestParam(defaultValue = "ACTIVE") PersonStatus status,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "30") int size) {
        Sort sort = Sort.by("lastName", "firstName");
        return PageResponse.of(studentService.search(q, status, Pages.of(page, size, sort)), StudentSummary::of);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('STUDENT_READ')")
    StudentDetails get(@PathVariable UUID id) {
        return details(studentService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('STUDENT_WRITE')")
    StudentDetails create(@Valid @RequestBody CreateStudentRequest request) {
        List<GuardianInput> guardians = request.guardians() == null ? List.of()
                : request.guardians().stream().map(GuardianRequest::toInput).toList();
        return details(studentService.create(request.student().toData(), guardians));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('STUDENT_WRITE')")
    StudentDetails update(@PathVariable UUID id, @Valid @RequestBody UpdateStudentRequest request) {
        return details(studentService.update(id, Versions.require(request.version()), request.student().toData()));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('STUDENT_WRITE')")
    StudentDetails archive(@PathVariable UUID id) {
        return details(studentService.archive(id));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('STUDENT_WRITE')")
    StudentDetails restore(@PathVariable UUID id) {
        return details(studentService.restore(id));
    }

    @PostMapping("/{id}/guardians")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('STUDENT_WRITE')")
    GuardianResponse addGuardian(@PathVariable UUID id, @Valid @RequestBody GuardianRequest request) {
        return GuardianResponse.of(studentService.addGuardian(id, request.toInput()));
    }

    @DeleteMapping("/{id}/guardians/{guardianId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('STUDENT_WRITE')")
    void removeGuardian(@PathVariable UUID id, @PathVariable UUID guardianId) {
        studentService.removeGuardian(id, guardianId);
    }

    @PostMapping("/{id}/guardians/{guardianId}/invite")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('INVITE_STAFF')")
    InviteLink inviteGuardian(@AuthenticationPrincipal CurrentMember member, @PathVariable UUID id,
                              @PathVariable UUID guardianId) {
        return InviteLink.of(studentService.inviteGuardian(member, id, guardianId));
    }

    private StudentDetails details(Student student) {
        return StudentDetails.of(student, studentService.guardiansOf(student.getId()));
    }

    record StudentFields(@NotBlank @Size(max = 100) String firstName,
                         @NotBlank @Size(max = 100) String lastName,
                         @Past LocalDate birthDate,
                         @Size(max = 32) String phone,
                         @Size(max = 1000) String note) {

        StudentData toData() {
            return new StudentData(firstName, lastName, birthDate, phone, note);
        }
    }

    record CreateStudentRequest(@NotNull @Valid StudentFields student,
                                @Size(max = 4) List<@Valid GuardianRequest> guardians) {
    }

    record UpdateStudentRequest(@NotNull @Valid StudentFields student, Long version) {
    }

    record GuardianRequest(@NotBlank @Size(max = 200) String fullName,
                           @NotBlank @Size(max = 32) String phone,
                           @NotNull GuardianRelation relation) {

        GuardianInput toInput() {
            return new GuardianInput(fullName, phone, relation);
        }
    }

    record StudentSummary(UUID id, String firstName, String lastName, String phone, PersonStatus status) {

        static StudentSummary of(Student s) {
            return new StudentSummary(s.getId(), s.getFirstName(), s.getLastName(), s.getPhone(), s.getStatus());
        }
    }

    record GuardianResponse(UUID id, String fullName, String phone, GuardianRelation relation, boolean linked) {

        static GuardianResponse of(LinkedGuardian g) {
            return new GuardianResponse(g.guardian().getId(), g.guardian().getFullName(), g.guardian().getPhone(),
                    g.relation(), g.guardian().getUserId() != null);
        }
    }

    record StudentDetails(UUID id, String firstName, String lastName, LocalDate birthDate, String phone, String note,
                          PersonStatus status, long version, List<GuardianResponse> guardians) {

        static StudentDetails of(Student s, List<LinkedGuardian> guardians) {
            return new StudentDetails(s.getId(), s.getFirstName(), s.getLastName(), s.getBirthDate(), s.getPhone(),
                    s.getNote(), s.getStatus(), s.getVersion(), guardians.stream().map(GuardianResponse::of).toList());
        }
    }
}
