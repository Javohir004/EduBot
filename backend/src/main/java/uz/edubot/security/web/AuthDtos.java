package uz.edubot.security.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uz.edubot.center.MembershipRole;
import uz.edubot.center.MembershipView;
import uz.edubot.user.AppUser;
import uz.edubot.user.PlatformRole;

/**
 * Request and response bodies of the authentication API.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record TelegramAuthRequest(@NotBlank @Size(max = 8192) String initData) {
    }

    public record SessionRequest(@NotNull UUID membershipId) {
    }

    public record AuthResponse(String accessToken, Instant expiresAt, MeResponse me) {
    }

    /**
     * @param activeMembershipId membership the current token is bound to; {@code null} means the
     *                           client must pick one (or the user has none yet)
     */
    public record MeResponse(UserDto user, List<MembershipDto> memberships, UUID activeMembershipId) {

        static MeResponse of(AppUser user, List<MembershipView> memberships, UUID activeMembershipId) {
            return new MeResponse(UserDto.of(user),
                    memberships.stream().map(MembershipDto::of).toList(),
                    activeMembershipId);
        }
    }

    public record UserDto(UUID id, String firstName, String lastName, String username, String languageCode,
                          PlatformRole platformRole) {

        static UserDto of(AppUser user) {
            return new UserDto(user.getId(), user.getFirstName(), user.getLastName(), user.getUsername(),
                    user.getLanguageCode(), user.getPlatformRole());
        }
    }

    public record MembershipDto(UUID id, UUID centerId, String centerName, MembershipRole role) {

        static MembershipDto of(MembershipView view) {
            return new MembershipDto(view.membershipId(), view.centerId(), view.centerName(), view.role());
        }
    }
}
