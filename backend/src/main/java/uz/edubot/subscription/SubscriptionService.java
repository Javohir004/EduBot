package uz.edubot.subscription;

import java.sql.Timestamp;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbc;
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

    /** Current phase of the center's subscription (cheap: one indexed query). */
    @Transactional(readOnly = true)
    public SubscriptionState state(UUID centerId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select s.id, p.code, p.name, s.status, s.ends_at, s.grace_until
                from subscription s join subscription_plan p on p.id = s.plan_id
                where s.center_id = ?
                order by case when s.status in ('TRIAL', 'ACTIVE') then 0 else 1 end, s.ends_at desc
                limit 1
                """, centerId);
        if (rows.isEmpty()) {
            return new SubscriptionState(null, null, null, null, null, null, SubscriptionState.Phase.NONE);
        }
        Map<String, Object> r = rows.getFirst();
        SubscriptionStatus status = SubscriptionStatus.valueOf((String) r.get("status"));
        Instant endsAt = toInstant(r.get("ends_at"));
        Instant graceUntil = r.get("grace_until") == null ? endsAt : toInstant(r.get("grace_until"));
        Instant now = clock.instant();
        SubscriptionState.Phase phase;
        if (status != SubscriptionStatus.TRIAL && status != SubscriptionStatus.ACTIVE) {
            phase = SubscriptionState.Phase.EXPIRED;
        } else if (now.isBefore(endsAt)) {
            phase = SubscriptionState.Phase.ACTIVE;
        } else if (now.isBefore(graceUntil)) {
            phase = SubscriptionState.Phase.GRACE;
        } else {
            phase = SubscriptionState.Phase.EXPIRED;
        }
        return new SubscriptionState((UUID) r.get("id"), (String) r.get("code"), (String) r.get("name"), status,
                endsAt, graceUntil, phase);
    }

    /**
     * Manual activation by the platform admin (MVP has no online payment): extends the current
     * subscription, or starts a new one, on the given plan.
     */
    @Transactional
    public SubscriptionState activate(UUID centerId, String planCode, int months, UUID adminId) {
        SubscriptionPlan plan = plans.findByCodeAndActiveTrue(planCode)
                .orElseThrow(() -> ApiException.notFound("PLAN_NOT_FOUND", "Subscription plan not found"));
        Instant now = clock.instant();
        List<Map<String, Object>> current = jdbc.queryForList(
                "select id, ends_at from subscription where center_id = ? and status in ('TRIAL', 'ACTIVE') for update",
                centerId);
        if (current.isEmpty()) {
            Instant endsAt = plusMonths(now, months);
            jdbc.update("""
                    insert into subscription (id, center_id, plan_id, status, starts_at, ends_at, grace_until,
                                              created_by, created_at, updated_at)
                    values (uuidv7(), ?, ?, 'ACTIVE', ?, ?, ?, ?, now(), now())
                    """, centerId, plan.getId(), Timestamp.from(now), Timestamp.from(endsAt),
                    Timestamp.from(endsAt.plus(GRACE_PERIOD)), adminId);
        } else {
            Instant currentEnd = toInstant(current.getFirst().get("ends_at"));
            Instant endsAt = plusMonths(currentEnd.isAfter(now) ? currentEnd : now, months);
            jdbc.update("""
                    update subscription set plan_id = ?, status = 'ACTIVE', ends_at = ?, grace_until = ?, updated_at = now()
                    where id = ?
                    """, plan.getId(), Timestamp.from(endsAt), Timestamp.from(endsAt.plus(GRACE_PERIOD)),
                    current.getFirst().get("id"));
        }
        return state(centerId);
    }

    /** Usage of the limited resources, for the subscription screen. */
    @Transactional(readOnly = true)
    public Map<String, Object> usage(UUID centerId) {
        return jdbc.queryForMap("""
                select (select count(*) from student where center_id = ? and status = 'ACTIVE') as students,
                       (select count(*) from teacher where center_id = ? and status = 'ACTIVE') as teachers,
                       (select count(*) from study_group where center_id = ? and status = 'ACTIVE') as groups
                """, centerId, centerId, centerId);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> limits(UUID centerId) {
        Map<String, Long> limits = new java.util.LinkedHashMap<>();
        jdbc.query("""
                select e.entitlement_key, coalesce(o.value, e.value)
                from subscription s
                join plan_entitlement e on e.plan_id = s.plan_id
                left join entitlement_override o on o.center_id = s.center_id and o.entitlement_key = e.entitlement_key
                where s.center_id = ? and s.status in ('TRIAL', 'ACTIVE')
                """, rs -> {
            limits.put(rs.getString(1), rs.getLong(2));
        }, centerId);
        return limits;
    }

    private static Instant plusMonths(Instant from, int months) {
        return from.atZone(uz.edubot.common.time.BusinessTime.ZONE).plusMonths(months).toInstant();
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime odt) {
            return odt.toInstant();
        }
        return (Instant) value;
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
