package uz.edubot.teacher;

import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
public class TeacherService {

    private final TeacherRepository teachers;
    private final EntitlementService entitlements;
    private final InviteService inviteService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<Teacher> search(String query, PersonStatus status, Pageable pageable) {
        return teachers.search(status, Pages.likePattern(query), pageable);
    }

    @Transactional(readOnly = true)
    public Teacher get(UUID id) {
        return teachers.findById(id)
                .orElseThrow(() -> ApiException.notFound("TEACHER_NOT_FOUND", "Teacher not found"));
    }

    @Transactional
    public Teacher create(String fullName, String phone, String subjects) {
        requireCapacity();
        return teachers.save(new Teacher(fullName.strip(), PhoneNumbers.optional(phone), blankToNull(subjects)));
    }

    @Transactional
    public Teacher update(UUID id, long expectedVersion, String fullName, String phone, String subjects) {
        Teacher teacher = get(id);
        Versions.check(teacher.getVersion(), expectedVersion);
        teacher.update(fullName.strip(), PhoneNumbers.optional(phone), blankToNull(subjects));
        return teacher;
    }

    @Transactional
    public Teacher archive(UUID id) {
        Teacher teacher = get(id);
        if (teacher.getStatus() == PersonStatus.ACTIVE) {
            teacher.archive(clock.instant());
        }
        return teacher;
    }

    @Transactional
    public Teacher restore(UUID id) {
        Teacher teacher = get(id);
        if (teacher.getStatus() == PersonStatus.ARCHIVED) {
            requireCapacity();
            teacher.restore();
        }
        return teacher;
    }

    @Transactional
    public CreatedInvite invite(CurrentMember member, UUID id) {
        Teacher teacher = get(id);
        if (teacher.getStatus() != PersonStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "TEACHER_ARCHIVED", "Teacher is archived");
        }
        if (teacher.getUserId() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_LINKED", "Teacher already uses the app");
        }
        if (teacher.getPhone() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHONE_REQUIRED", "Add a phone number first");
        }
        return inviteService.createForProfile(member, MembershipRole.TEACHER, teacher.getPhone(), teacher.getId());
    }

    /** Links the Telegram account once the teacher accepts the invite (runs in the accept transaction). */
    @EventListener
    void onInviteAccepted(InviteAcceptedEvent event) {
        if (event.role() != MembershipRole.TEACHER || event.targetId() == null) {
            return;
        }
        teachers.linkUser(event.centerId(), event.targetId(), event.userId());
    }

    private void requireCapacity() {
        entitlements.requireCapacity(TenantContext.require(), EntitlementKey.MAX_TEACHERS,
                () -> teachers.countByStatus(PersonStatus.ACTIVE));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
