package uz.edubot.student;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.edubot.center.MembershipRole;
import uz.edubot.common.PersonStatus;
import uz.edubot.common.error.ApiException;
import uz.edubot.common.phone.PhoneNumbers;
import uz.edubot.common.web.Pages;
import uz.edubot.common.web.Versions;
import uz.edubot.invite.CreatedInvite;
import uz.edubot.invite.InviteAcceptedEvent;
import uz.edubot.invite.InviteService;
import uz.edubot.security.CurrentMember;
import uz.edubot.subscription.EntitlementKey;
import uz.edubot.subscription.EntitlementService;
import uz.edubot.tenancy.TenantContext;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository students;
    private final GuardianRepository guardians;
    private final StudentGuardianRepository links;
    private final EntitlementService entitlements;
    private final InviteService inviteService;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public record GuardianInput(String fullName, String phone, GuardianRelation relation) {
    }

    public record LinkedGuardian(Guardian guardian, GuardianRelation relation) {
    }

    @Transactional(readOnly = true)
    public Page<Student> search(String query, PersonStatus status, Pageable pageable) {
        return students.search(status, Pages.likePattern(query), pageable);
    }

    @Transactional(readOnly = true)
    public Student get(UUID id) {
        return students.findById(id)
                .orElseThrow(() -> ApiException.notFound("STUDENT_NOT_FOUND", "Student not found"));
    }

    @Transactional(readOnly = true)
    public Map<UUID, Student> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return students.findByIds(ids).stream().collect(Collectors.toMap(Student::getId, Function.identity()));
    }

    @Transactional(readOnly = true)
    public List<Student> childrenOfUser(UUID userId) {
        return students.findChildrenOfUser(userId);
    }

    /** Telegram user ids of guardians who use the app, per student. */
    @Transactional(readOnly = true)
    public Map<UUID, List<UUID>> guardianUserIds(Collection<UUID> studentIds) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        return links.findGuardianUsers(studentIds).stream().collect(Collectors.groupingBy(
                row -> (UUID) row[0], Collectors.mapping(row -> (UUID) row[1], Collectors.toList())));
    }

    /** Fails unless the student exists in the current center and is not archived. */
    @Transactional(readOnly = true)
    public Student requireActive(UUID id) {
        Student student = get(id);
        if (student.getStatus() != PersonStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "STUDENT_ARCHIVED", "Student is archived");
        }
        return student;
    }

    @Transactional(readOnly = true)
    public List<LinkedGuardian> guardiansOf(UUID studentId) {
        List<StudentGuardian> studentLinks = links.findByStudent(studentId);
        if (studentLinks.isEmpty()) {
            return List.of();
        }
        Map<UUID, Guardian> byId = guardians.findByIds(studentLinks.stream().map(StudentGuardian::getGuardianId).toList())
                .stream().collect(Collectors.toMap(Guardian::getId, Function.identity()));
        return studentLinks.stream()
                .filter(l -> byId.containsKey(l.getGuardianId()))
                .map(l -> new LinkedGuardian(byId.get(l.getGuardianId()), l.getRelation()))
                .toList();
    }

    @Transactional
    public Student create(StudentData data, List<GuardianInput> guardianInputs) {
        requireCapacity();
        Student student = students.save(new Student(normalize(data)));
        guardianInputs.forEach(g -> addGuardian(student.getId(), g));
        return student;
    }

    @Transactional
    public Student update(UUID id, long expectedVersion, StudentData data) {
        Student student = get(id);
        Versions.check(student.getVersion(), expectedVersion);
        student.update(normalize(data));
        return student;
    }

    @Transactional
    public Student archive(UUID id) {
        Student student = get(id);
        if (student.getStatus() == PersonStatus.ACTIVE) {
            student.archive(clock.instant());
            // Groups end the student's active enrollments (same transaction).
            events.publishEvent(new StudentArchivedEvent(student.getId()));
        }
        return student;
    }

    @Transactional
    public Student restore(UUID id) {
        Student student = get(id);
        if (student.getStatus() == PersonStatus.ARCHIVED) {
            requireCapacity();
            student.restore();
        }
        return student;
    }

    /**
     * Links a guardian to the student. A guardian with the same phone in this center is reused
     * (siblings share one parent account).
     */
    @Transactional
    public LinkedGuardian addGuardian(UUID studentId, GuardianInput input) {
        Student student = get(studentId);
        String phone = PhoneNumbers.require(input.phone());
        Guardian guardian = guardians.findByPhone(phone)
                .orElseGet(() -> guardians.save(new Guardian(input.fullName().strip(), phone)));
        if (links.findLink(student.getId(), guardian.getId()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "GUARDIAN_ALREADY_ADDED", "Guardian is already linked");
        }
        links.save(new StudentGuardian(student.getId(), guardian.getId(), input.relation()));
        return new LinkedGuardian(guardian, input.relation());
    }

    @Transactional
    public void removeGuardian(UUID studentId, UUID guardianId) {
        StudentGuardian link = links.findLink(get(studentId).getId(), guardianId)
                .orElseThrow(() -> ApiException.notFound("GUARDIAN_NOT_FOUND", "Guardian not found"));
        links.delete(link);
    }

    @Transactional
    public CreatedInvite inviteGuardian(CurrentMember member, UUID studentId, UUID guardianId) {
        links.findLink(get(studentId).getId(), guardianId)
                .orElseThrow(() -> ApiException.notFound("GUARDIAN_NOT_FOUND", "Guardian not found"));
        Guardian guardian = guardians.findById(guardianId)
                .orElseThrow(() -> ApiException.notFound("GUARDIAN_NOT_FOUND", "Guardian not found"));
        if (guardian.getUserId() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_LINKED", "Guardian already uses the app");
        }
        return inviteService.createForProfile(member, MembershipRole.PARENT, guardian.getPhone(), guardian.getId());
    }

    /** Links the parent's Telegram account once they accept the invite (runs in the accept transaction). */
    @EventListener
    void onInviteAccepted(InviteAcceptedEvent event) {
        if (event.role() != MembershipRole.PARENT || event.targetId() == null) {
            return;
        }
        guardians.linkUser(event.centerId(), event.targetId(), event.userId());
    }

    private void requireCapacity() {
        entitlements.requireCapacity(TenantContext.require(), EntitlementKey.MAX_STUDENTS,
                () -> students.countByStatus(PersonStatus.ACTIVE));
    }

    private static StudentData normalize(StudentData data) {
        return new StudentData(data.firstName().strip(), data.lastName().strip(), data.birthDate(),
                PhoneNumbers.optional(data.phone()),
                data.note() == null || data.note().isBlank() ? null : data.note().strip());
    }
}
