package uz.edubot.subscription;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.edubot.common.error.ApiException;

/**
 * The single place where plan limits are enforced.
 */
@Service
@RequiredArgsConstructor
public class EntitlementService {

    private final EntityManager entityManager;

    /**
     * Fails when adding one more item would exceed the limit. Locks the center's current
     * subscription row, so concurrent creates are serialized and cannot both pass the check.
     * Must run inside the caller's write transaction.
     *
     * @param currentCount counts the items that already use the limit (evaluated after the lock)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireCapacity(UUID centerId, EntitlementKey key, LongSupplier currentCount) {
        long limit = lockedLimit(centerId, key);
        if (currentCount.getAsLong() >= limit) {
            throw new ApiException(HttpStatus.CONFLICT, "PLAN_LIMIT_REACHED",
                    "Plan limit reached: " + key.name());
        }
    }

    private long lockedLimit(UUID centerId, EntitlementKey key) {
        @SuppressWarnings("unchecked")
        List<Number> rows = entityManager.createNativeQuery("""
                        select coalesce(o.value, e.value, 0)
                        from subscription s
                        left join plan_entitlement e on e.plan_id = s.plan_id and e.entitlement_key = :key
                        left join entitlement_override o on o.center_id = s.center_id and o.entitlement_key = :key
                        where s.center_id = :centerId and s.status in ('TRIAL', 'ACTIVE')
                        for update of s
                        """)
                .setParameter("key", key.name())
                .setParameter("centerId", centerId)
                .getResultList();
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "SUBSCRIPTION_REQUIRED",
                    "The center has no active subscription");
        }
        return rows.getFirst().longValue();
    }
}
