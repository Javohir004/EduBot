package uz.edubot.security.web;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.edubot.center.MembershipService;
import uz.edubot.center.MembershipView;
import uz.edubot.common.error.ApiException;
import uz.edubot.security.AccessToken;
import uz.edubot.security.CurrentMember;
import uz.edubot.security.PlatformProperties;
import uz.edubot.security.TokenService;
import uz.edubot.security.telegram.InvalidInitDataException;
import uz.edubot.security.telegram.TelegramInitData;
import uz.edubot.security.telegram.TelegramInitDataValidator;
import uz.edubot.security.telegram.TelegramUser;
import uz.edubot.security.web.AuthDtos.AuthResponse;
import uz.edubot.security.web.AuthDtos.MeResponse;
import uz.edubot.user.AppUser;
import uz.edubot.user.TelegramProfile;
import uz.edubot.user.UserService;

@Slf4j
@Service
@RequiredArgsConstructor
class AuthService {

    private final TelegramInitDataValidator initDataValidator;
    private final UserService userService;
    private final MembershipService membershipService;
    private final TokenService tokenService;
    private final PlatformProperties platformProperties;

    /**
     * Exchanges verified Telegram initData for an access token. With exactly one active membership
     * the token is bound to it; otherwise the client selects one via {@link #selectMembership}.
     */
    @Transactional
    AuthResponse loginWithTelegram(String rawInitData) {
        TelegramInitData initData;
        try {
            initData = initDataValidator.validate(rawInitData);
        } catch (InvalidInitDataException e) {
            log.warn("Rejected Telegram initData: {}", e.getReason());
            throw e;
        }
        TelegramUser tg = initData.user();
        AppUser user = userService.upsertFromTelegram(
                new TelegramProfile(tg.id(), tg.firstName(), tg.lastName(), tg.username(), tg.languageCode()),
                platformProperties.isPlatformAdmin(tg.id()));

        List<MembershipView> memberships = membershipService.findActiveForUser(user.getId());
        MembershipView active = memberships.size() == 1 ? memberships.getFirst() : null;
        log.info("Telegram login userId={} memberships={}", user.getId(), memberships.size());
        return respond(user, memberships, active);
    }

    @Transactional(readOnly = true)
    AuthResponse selectMembership(CurrentMember member, UUID membershipId) {
        AppUser user = loadUser(member);
        List<MembershipView> memberships = membershipService.findActiveForUser(user.getId());
        MembershipView selected = memberships.stream()
                .filter(m -> m.membershipId().equals(membershipId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("MEMBERSHIP_NOT_FOUND", "Membership not found"));
        return respond(user, memberships, selected);
    }

    @Transactional(readOnly = true)
    MeResponse me(CurrentMember member) {
        AppUser user = loadUser(member);
        // The membership in the token was already re-validated against the database.
        return MeResponse.of(user, membershipService.findActiveForUser(user.getId()), member.membershipId());
    }

    private AuthResponse respond(AppUser user, List<MembershipView> memberships, MembershipView active) {
        AccessToken token = tokenService.issue(user.getId(), active);
        UUID activeId = active == null ? null : active.membershipId();
        return new AuthResponse(token.value(), token.expiresAt(), MeResponse.of(user, memberships, activeId));
    }

    private AppUser loadUser(CurrentMember member) {
        return userService.findById(member.userId())
                .orElseThrow(() -> ApiException.unauthorized("USER_NOT_FOUND", "Authentication required"));
    }
}
