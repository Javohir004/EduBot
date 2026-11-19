package uz.edubot.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import uz.edubot.notification.NotificationSender.SendResult;
import uz.edubot.user.UserService;

/**
 * Sends due notifications from the outbox. Rows are claimed with {@code FOR UPDATE SKIP LOCKED},
 * so overlapping runs never send the same message twice. Throttled well below Telegram's limits.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    static final int BATCH_SIZE = 50;
    static final int MAX_ATTEMPTS = 5;
    private static final Duration THROTTLE = Duration.ofMillis(40);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final NotificationSender sender;
    private final UserService userService;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    private record Due(UUID id, UUID userId, long telegramId, String language, boolean botBlocked,
                       NotificationType type, String payload, int attempts) {
    }

    @Scheduled(fixedDelayString = "${edubot.notifications.dispatch-interval:10s}")
    @SchedulerLock(name = "notification-dispatch")
    public void scheduledDispatch() {
        int sent = dispatchDue();
        if (sent > 0) {
            log.info("Dispatched {} notifications", sent);
        }
    }

    /** @return number of notifications processed in this run */
    public int dispatchDue() {
        Integer processed = tx.execute(status -> {
            List<Due> due = jdbc.query("""
                    select n.id, n.recipient_user_id, u.telegram_id, u.language_code, u.bot_blocked,
                           n.type, n.payload::text as payload, n.attempts
                    from notification n join app_user u on u.id = n.recipient_user_id
                    where n.status = 'PENDING' and n.scheduled_at <= ?
                    order by n.scheduled_at
                    limit ?
                    for update of n skip locked
                    """, (rs, i) -> new Due(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getLong(3),
                    rs.getString(4), rs.getBoolean(5), NotificationType.valueOf(rs.getString(6)), rs.getString(7),
                    rs.getInt(8)), java.sql.Timestamp.from(clock.instant()), BATCH_SIZE);
            due.forEach(this::process);
            return due.size();
        });
        return processed == null ? 0 : processed;
    }

    private void process(Due n) {
        if (n.botBlocked()) {
            fail(n.id(), "User blocked the bot");
            return;
        }
        Map<String, Object> payload = jsonMapper.readValue(n.payload(), new TypeReference<Map<String, Object>>() { });
        SendResult result = sender.send(n.telegramId(), NotificationTemplates.render(n.type(), payload, n.language()));
        switch (result) {
            case SendResult.Sent ignored -> jdbc.update(
                    "update notification set status = 'SENT', sent_at = now(), attempts = attempts + 1, "
                            + "updated_at = now() where id = ?", n.id());
            case SendResult.Blocked ignored -> {
                userService.markBotBlocked(n.userId(), true);
                fail(n.id(), "User blocked the bot");
            }
            case SendResult.RetryAfter retry -> reschedule(n, Duration.ofSeconds(Math.max(retry.seconds(), 1)), "Rate limited");
            case SendResult.Failed failed -> {
                if (n.attempts() + 1 >= MAX_ATTEMPTS) {
                    fail(n.id(), failed.error());
                } else {
                    reschedule(n, Duration.ofMinutes(1L << n.attempts()), failed.error());
                }
            }
        }
        pause();
    }

    private void reschedule(Due n, Duration delay, String error) {
        Instant next = clock.instant().plus(delay);
        jdbc.update("update notification set attempts = attempts + 1, scheduled_at = ?, last_error = ?, "
                + "updated_at = now() where id = ?", java.sql.Timestamp.from(next), truncate(error), n.id());
    }

    private void fail(UUID id, String error) {
        jdbc.update("update notification set status = 'FAILED', attempts = attempts + 1, last_error = ?, "
                + "updated_at = now() where id = ?", truncate(error), id);
    }

    private static String truncate(String error) {
        return error == null ? null : error.substring(0, Math.min(error.length(), 500));
    }

    private static void pause() {
        try {
            Thread.sleep(THROTTLE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
