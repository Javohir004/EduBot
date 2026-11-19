package uz.edubot.invite;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.edubot.center.CenterService;
import uz.edubot.center.EducationCenter;
import uz.edubot.center.MembershipRole;
import uz.edubot.center.MembershipService;
import uz.edubot.common.error.ApiException;
import uz.edubot.common.phone.PhoneNumbers;
import uz.edubot.security.CurrentMember;
import uz.edubot.security.Permission;
import uz.edubot.user.AppUser;
import uz.edubot.user.TelegramProfile;
import uz.edubot.user.UserService;

@Slf4j
@Service
@RequiredArgsConstructor
public class InviteService {

    static final Duration VALIDITY = Duration.ofDays(7);

    private final InviteRepository invites;
    private final CenterService centerService;
    private final MembershipService membershipService;
    private final UserService userService;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    @Value("${edubot.telegram.bot-username}")
    private String botUsername;

    /**
     * Invite created by center staff. Owners are invited only by the platform.
     */
    @Transactional
    public CreatedInvite createByMember(CurrentMember member, MembershipRole role, String phone) {
        Permission required = switch (role) {
            case OWNER -> null;
            case ADMIN -> Permission.INVITE_ADMIN;
            case TEACHER, PARENT -> Permission.INVITE_STAFF;
        };
        if (required == null || !member.has(required)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INVITE_ROLE_FORBIDDEN", "You cannot invite this role");
        }
        return create(member.requireCenterId(), role, phone, null, member.userId());
    }

    /**
     * Invite that links the accepting user to an existing teacher or guardian profile.
     * The caller has already verified that the profile belongs to the member's center.
     */
    @Transactional
    public CreatedInvite createForProfile(CurrentMember member, MembershipRole role, String phone, UUID targetId) {
        if (role != MembershipRole.TEACHER && role != MembershipRole.PARENT) {
            throw new IllegalArgumentException("Profiles exist only for teachers and parents");
        }
        return create(member.requireCenterId(), role, phone, targetId, member.userId());
    }

    @Transactional
    public CreatedInvite create(UUID centerId, MembershipRole role, String rawPhone, UUID targetId, UUID createdBy) {
        String phone = PhoneNumbers.normalize(rawPhone)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PHONE", "Invalid phone number"));
        String token = InviteTokens.generate();
        Invite invite = invites.save(new Invite(centerId, role, phone, InviteTokens.hash(token),
                clock.instant().plus(VALIDITY), targetId, createdBy));
        log.info("Invite created id={} centerId={} role={}", invite.getId(), centerId, role);
        return new CreatedInvite(invite, "https://t.me/" + botUsername + "?start=" + InviteTokens.START_PREFIX + token);
    }

    @Transactional(readOnly = true)
    public List<Invite> listPending(UUID centerId) {
        return invites.findPending(centerId, clock.instant());
    }

    @Transactional
    public void revoke(UUID centerId, UUID inviteId) {
        Invite invite = invites.findByIdAndCenterId(inviteId, centerId)
                .orElseThrow(() -> ApiException.notFound("INVITE_NOT_FOUND", "Invite not found"));
        if (invite.getUsedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "INVITE_ALREADY_USED", "Invite has already been used");
        }
        if (invite.getRevokedAt() == null) {
            invite.revoke(clock.instant());
        }
    }

    /**
     * Accepts an invite on behalf of the Telegram user who opened the link. The phone number must be
     * the one Telegram verified for this user and must match the phone the invite was issued for.
     *
     * @param startParameter the {@code /start} parameter, e.g. {@code inv_<token>}
     */
    @Transactional
    public AcceptedInvite accept(String startParameter, TelegramProfile profile, String contactPhone) {
        if (startParameter == null || !startParameter.startsWith(InviteTokens.START_PREFIX)) {
            throw ApiException.notFound("INVITE_NOT_FOUND", "Invite not found");
        }
        String token = startParameter.substring(InviteTokens.START_PREFIX.length());
        Invite invite = invites.findByTokenHashForUpdate(InviteTokens.hash(token))
                .orElseThrow(() -> ApiException.notFound("INVITE_NOT_FOUND", "Invite not found"));

        AppUser user = userService.upsertFromTelegram(profile);
        EducationCenter center = centerService.findById(invite.getCenterId())
                .orElseThrow(() -> new IllegalStateException("Invite without center: " + invite.getId()));

        if (invite.getUsedAt() != null) {
            if (user.getId().equals(invite.getUsedBy())) {
                return new AcceptedInvite(center.getId(), center.getName(), invite.getRole());
            }
            throw new ApiException(HttpStatus.GONE, "INVITE_ALREADY_USED", "Invite has already been used");
        }
        if (!invite.isPending(clock.instant())) {
            throw new ApiException(HttpStatus.GONE, "INVITE_EXPIRED", "Invite is expired or revoked");
        }
        String phone = PhoneNumbers.normalize(contactPhone).orElse(null);
        if (phone == null || !phone.equals(invite.getExpectedPhone())) {
            log.warn("Invite phone mismatch id={} userId={}", invite.getId(), user.getId());
            throw new ApiException(HttpStatus.FORBIDDEN, "INVITE_PHONE_MISMATCH",
                    "Phone number does not match the invite");
        }

        userService.markPhoneVerified(user.getId(), phone);
        membershipService.grant(invite.getCenterId(), user.getId(), invite.getRole());
        invite.markUsed(user.getId(), clock.instant());
        // Listeners (teacher, student modules) link their profile inside this transaction.
        events.publishEvent(new InviteAcceptedEvent(invite.getCenterId(), invite.getRole(), invite.getTargetId(),
                user.getId()));
        log.info("Invite accepted id={} userId={} role={}", invite.getId(), user.getId(), invite.getRole());
        return new AcceptedInvite(center.getId(), center.getName(), invite.getRole());
    }
}
