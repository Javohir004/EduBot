package uz.edubot.security;

import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import uz.edubot.center.MembershipRole;
import uz.edubot.common.error.ApiException;
import uz.edubot.user.PlatformRole;

/**
 * The authenticated caller, resolved from the database on every request.
 * Membership fields are {@code null} when the token is not bound to a center.
 */
public record CurrentMember(
        UUID userId,
        PlatformRole platformRole,
        UUID membershipId,
        UUID centerId,
        MembershipRole role,
        Set<Permission> permissions) {

    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }

    public UUID requireCenterId() {
        if (centerId == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CENTER_REQUIRED", "Select a center first");
        }
        return centerId;
    }
}
