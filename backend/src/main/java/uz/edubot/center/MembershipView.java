package uz.edubot.center;

import java.util.UUID;

/**
 * Read model of an active membership together with its center name.
 */
public record MembershipView(UUID membershipId, UUID centerId, String centerName, MembershipRole role) {
}
