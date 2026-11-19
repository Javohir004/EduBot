package uz.edubot.subscription;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionView(UUID centerId, String planCode, String planName, SubscriptionStatus status,
                               Instant endsAt) {
}
