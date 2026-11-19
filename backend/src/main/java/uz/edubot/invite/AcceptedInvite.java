package uz.edubot.invite;

import java.util.UUID;
import uz.edubot.center.MembershipRole;

public record AcceptedInvite(UUID centerId, String centerName, MembershipRole role) {
}
