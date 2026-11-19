package uz.edubot.audit;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Append-only audit trail for sensitive operations (money corrections, role changes). Written in
 * the same transaction as the change, so an audited change cannot happen without its record.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final JdbcTemplate jdbc;
    private final JsonMapper jsonMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID centerId, UUID actorUserId, String action, String entityType, UUID entityId,
                       Map<String, Object> data) {
        jdbc.update("""
                insert into audit_log (id, center_id, actor_user_id, action, entity_type, entity_id, data, created_at)
                values (uuidv7(), ?, ?, ?, ?, ?, ?::jsonb, now())
                """, centerId, actorUserId, action, entityType, entityId, jsonMapper.writeValueAsString(data));
    }
}
