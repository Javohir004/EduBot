package uz.edubot.notification;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes notifications into the outbox inside the caller's transaction. The dedup key makes
 * enqueueing idempotent: the same event never produces two messages.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final JdbcTemplate jdbc;
    private final JsonMapper jsonMapper;

    /**
     * Inserts a pending notification. If one with the same key exists it is left alone, unless it
     * was cancelled, in which case it is re-armed (e.g. ABSENT -> PRESENT -> ABSENT again).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(UUID centerId, UUID recipientUserId, NotificationType type, Map<String, Object> payload,
                        String dedupKey, Instant scheduledAt) {
        jdbc.update("""
                insert into notification (id, center_id, recipient_user_id, type, payload, dedup_key, status,
                                          scheduled_at, created_at, updated_at)
                values (uuidv7(), ?, ?, ?, ?::jsonb, ?, 'PENDING', ?, now(), now())
                on conflict (dedup_key) do update
                    set status = 'PENDING', scheduled_at = excluded.scheduled_at, payload = excluded.payload,
                        attempts = 0, updated_at = now()
                    where notification.status = 'CANCELLED'
                """, centerId, recipientUserId, type.name(), jsonMapper.writeValueAsString(payload), dedupKey,
                java.sql.Timestamp.from(scheduledAt));
    }

    /** Cancels notifications that have not been sent yet; sent ones cannot be recalled. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int cancelPending(String dedupKeyPrefix) {
        return jdbc.update("""
                update notification set status = 'CANCELLED', updated_at = now()
                where status = 'PENDING' and dedup_key like ? escape '\\'
                """, escapeLike(dedupKeyPrefix) + "%");
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
