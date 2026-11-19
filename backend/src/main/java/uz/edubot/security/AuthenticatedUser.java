package uz.edubot.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import uz.edubot.common.error.ApiException;

/**
 * Identity extracted from a validated access token.
 */
public record AuthenticatedUser(UUID userId, Optional<UUID> membershipId) {

    public static AuthenticatedUser from(Jwt jwt) {
        try {
            UUID userId = UUID.fromString(jwt.getSubject());
            String mid = jwt.getClaimAsString(TokenService.MEMBERSHIP_CLAIM);
            return new AuthenticatedUser(userId, Optional.ofNullable(mid).map(UUID::fromString));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.unauthorized("INVALID_TOKEN", "Authentication required");
        }
    }
}
