package uz.edubot.subscription;

import java.time.Instant;
import java.util.UUID;

/**
 * The center's subscription as seen right now.
 *
 * @param phase ACTIVE (paid or trial), GRACE (ended, still writable for a few days),
 *              EXPIRED (read-only) or NONE (never subscribed)
 */
public record SubscriptionState(UUID subscriptionId, String planCode, String planName, SubscriptionStatus status,
                                Instant endsAt, Instant graceUntil, Phase phase) {

    public enum Phase {
        ACTIVE, GRACE, EXPIRED, NONE
    }

    public boolean allowsWrites() {
        return phase == Phase.ACTIVE || phase == Phase.GRACE;
    }
}
