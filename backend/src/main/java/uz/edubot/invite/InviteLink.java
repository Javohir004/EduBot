package uz.edubot.invite;

import java.time.Instant;

/** API response for a freshly created invite: the link is shown once and never stored. */
public record InviteLink(String link, Instant expiresAt) {

    public static InviteLink of(CreatedInvite created) {
        return new InviteLink(created.link(), created.invite().getExpiresAt());
    }
}
