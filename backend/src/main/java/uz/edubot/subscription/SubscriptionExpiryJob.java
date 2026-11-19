package uz.edubot.subscription;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.edubot.common.time.BusinessTime;
import uz.edubot.notification.NotificationService;
import uz.edubot.notification.NotificationType;

/**
 * Daily: closes subscriptions whose grace period is over and reminds owners 7, 3 and 1 days
 * before the end date (each reminder at most once, via the dedup key).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionExpiryJob {

    static final Set<Long> REMINDER_DAYS = Set.of(7L, 3L, 1L);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(BusinessTime.ZONE);

    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final Clock clock;

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Tashkent")
    @SchedulerLock(name = "subscription-expiry")
    @Transactional
    public void run() {
        Instant now = clock.instant();
        int expired = jdbc.update("""
                update subscription set status = 'EXPIRED', updated_at = now()
                where status in ('TRIAL', 'ACTIVE') and coalesce(grace_until, ends_at) < ?
                """, Timestamp.from(now));
        if (expired > 0) {
            log.info("Expired {} subscriptions", expired);
        }

        List<Map<String, Object>> ending = jdbc.queryForList("""
                select s.id, s.center_id, s.ends_at, c.name
                from subscription s join education_center c on c.id = s.center_id
                where s.status in ('TRIAL', 'ACTIVE') and s.ends_at > ? and s.ends_at <= ? and c.status = 'ACTIVE'
                """, Timestamp.from(now), Timestamp.from(now.plus(Duration.ofDays(7))));
        for (Map<String, Object> row : ending) {
            Instant endsAt = ((Timestamp) row.get("ends_at")).toInstant();
            long daysLeft = (long) Math.ceil(Duration.between(now, endsAt).toHours() / 24.0);
            if (!REMINDER_DAYS.contains(daysLeft)) {
                continue;
            }
            UUID centerId = (UUID) row.get("center_id");
            List<UUID> owners = jdbc.queryForList("""
                    select user_id from center_membership where center_id = ? and role = 'OWNER' and status = 'ACTIVE'
                    """, UUID.class, centerId);
            Map<String, Object> payload = Map.of("centerName", row.get("name"), "date", DATE.format(endsAt));
            for (UUID owner : owners) {
                notifications.enqueue(centerId, owner, NotificationType.SUBSCRIPTION_EXPIRING, payload,
                        "SUB_EXPIRING:" + row.get("id") + ":" + daysLeft + ":" + owner, now);
            }
        }
    }
}
