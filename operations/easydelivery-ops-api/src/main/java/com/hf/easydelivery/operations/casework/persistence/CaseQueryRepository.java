package com.hf.easydelivery.operations.casework.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/** Read-only projections for operational cases, audit history and outbox monitoring. */
@Repository
public class CaseQueryRepository {
    private final JdbcTemplate jdbc;

    public CaseQueryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> outboxEvents(String status, int limit) {
        String projection = "SELECT id, aggregate_type, aggregate_id, event_type, event_key, partner_id, status, attempt_count, next_attempt_at, locked_at, acknowledged_at, last_error, created_at FROM outbox_event";
        if (status != null && !status.isBlank()) {
            return jdbc.queryForList(projection + " WHERE status = ? ORDER BY id DESC LIMIT ?", status, limit);
        }
        return jdbc.queryForList(projection + " ORDER BY id DESC LIMIT ?", limit);
    }

    public List<Map<String, Object>> auditLogs(String resourceType, String resourceId, int limit) {
        String projection = "SELECT id, operator_user_id, actor_type, actor_id, station_id, action_code, resource_type, resource_id, outcome, reason_text, occurred_at FROM operation_audit_log";
        if (resourceType != null && !resourceType.isBlank() && resourceId != null && !resourceId.isBlank()) {
            return jdbc.queryForList(projection + " WHERE resource_type = ? AND resource_id = ? ORDER BY id DESC LIMIT ?", resourceType, resourceId, limit);
        }
        if (resourceType != null && !resourceType.isBlank()) {
            return jdbc.queryForList(projection + " WHERE resource_type = ? ORDER BY id DESC LIMIT ?", resourceType, limit);
        }
        return jdbc.queryForList(projection + " ORDER BY id DESC LIMIT ?", limit);
    }
}
