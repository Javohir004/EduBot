package uz.edubot.invite;

/**
 * A new invite together with its link. The link contains the raw token and is shown only once.
 */
public record CreatedInvite(Invite invite, String link) {
}
