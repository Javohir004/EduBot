package uz.edubot.invite;

import java.util.UUID;
import uz.edubot.center.MembershipRole;

/**
 * Published synchronously inside the accepting transaction.
 *
 * @param targetId teacher or guardian profile to link, or {@code null}
 */
public record InviteAcceptedEvent(UUID centerId, MembershipRole role, UUID targetId, UUID userId) {
}
