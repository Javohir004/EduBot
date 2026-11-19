package uz.edubot.subscription;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.edubot.common.error.ApiException;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    /** Days of full access after {@code ends_at} before the center becomes read-only. */
    static final Duration GRACE_PERIOD = Duration.ofDays(5);

    private final SubscriptionPlanRepository plans;
    private final SubscriptionRepository subscriptions;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<SubscriptionPlan> activePlans() {
        return plans.findByActiveTrueOrderBySortOrder();
    }

    /**
     * Starts a trial on the given plan. The partial unique index guarantees at most one current
     * subscription per center.
     */
    @Transactional
    public SubscriptionView startTrial(UUID centerId, String planCode, Duration length, UUID createdBy) {
        SubscriptionPlan plan = plans.findByCodeAndActiveTrue(planCode)
                .orElseThrow(() -> ApiException.notFound("PLAN_NOT_FOUND", "Subscription plan not found"));
        Instant now = clock.instant();
        Instant endsAt = now.plus(length);
        Subscription subscription = subscriptions.save(new Subscription(centerId, plan.getId(),
                SubscriptionStatus.TRIAL, now, endsAt, endsAt.plus(GRACE_PERIOD), createdBy));
        return new SubscriptionView(centerId, plan.getCode(), plan.getName(), subscription.getStatus(), endsAt);
    }

    @Transactional(readOnly = true)
    public Map<UUID, SubscriptionView> findCurrent(Collection<UUID> centerIds) {
        if (centerIds.isEmpty()) {
            return Map.of();
        }
        return subscriptions.findCurrentByCenterIds(centerIds).stream()
                .collect(Collectors.toMap(SubscriptionView::centerId, Function.identity()));
    }
}
