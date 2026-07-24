package com.hf.easydelivery.operations;

import com.hf.easydelivery.common.exception.BizException;
import com.hf.easydelivery.config.OperationsAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Profile("!memory")
public class ConfigCaseOperationsService {
    private final JdbcTemplate jdbc;
    private final OperationsAccess access;

    public ConfigCaseOperationsService(JdbcTemplate jdbc, OperationsAccess access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    public List<Map<String, Object>> listOutboxEvents(String statusFilter, int limit) {
        int maxLimit = Math.min(Math.max(limit, 1), 200);
        if (statusFilter != null && !statusFilter.isBlank()) {
            return jdbc.queryForList("""
                    SELECT id, aggregate_type, aggregate_id, event_type, event_key, partner_id,
                           status, attempt_count, next_attempt_at, locked_at, acknowledged_at, last_error, created_at
                    FROM outbox_event
                    WHERE status = ?
                    ORDER BY id DESC LIMIT ?
                    """, statusFilter, maxLimit);
        }
        return jdbc.queryForList("""
                SELECT id, aggregate_type, aggregate_id, event_type, event_key, partner_id,
                       status, attempt_count, next_attempt_at, locked_at, acknowledged_at, last_error, created_at
                FROM outbox_event
                ORDER BY id DESC LIMIT ?
                """, maxLimit);
    }

    @Transactional
    public Map<String, Object> replayOutboxEvent(long eventId, HttpServletRequest request) {
        List<Map<String, Object>> events = jdbc.queryForList("SELECT id, status FROM outbox_event WHERE id = ? FOR UPDATE", eventId);
        if (events.isEmpty()) {
            throw new BizException("OUTBOX.EVENT.NOT.FOUND", "Outbox event not found: " + eventId);
        }
        Long reviewer = request.getAttribute("operatorUserId") instanceof Long id ? id : null;

        jdbc.update("""
                UPDATE outbox_event
                SET status = 'PENDING', attempt_count = 0, next_attempt_at = CURRENT_TIMESTAMP(3),
                    locked_at = NULL, locked_by = NULL, last_error = NULL
                WHERE id = ?
                """, eventId);

        try {
            jdbc.update("INSERT INTO operation_audit_log(operator_user_id,actor_type,actor_id,station_id,action_code,resource_type,resource_id,outcome,reason_text,after_json,request_id,occurred_at) VALUES (?,'OPERATOR',?,NULL, 'OUTBOX_REPLAY','OUTBOX_EVENT',?,'SUCCESS','Outbox event replayed',JSON_OBJECT('eventId',?, 'newStatus', 'PENDING'),?,CURRENT_TIMESTAMP(3))",
                    reviewer, reviewer, String.valueOf(eventId), eventId, request.getHeader("X-Request-Id"));
        } catch (Exception ignored) {}

        return Map.of("eventId", eventId, "status", "PENDING", "message", "Outbox event reset to PENDING for immediate retry");
    }

    @Transactional
    public Map<String, Object> addCaseAction(long caseId, CaseActionRequest body, HttpServletRequest request) {
        List<Map<String, Object>> cases = jdbc.queryForList("SELECT id, status, station_id FROM operational_case WHERE id = ? FOR UPDATE", caseId);
        if (cases.isEmpty()) {
            throw new BizException("CASE.NOT.FOUND", "Operational case not found: " + caseId);
        }
        Map<String, Object> caseRow = cases.get(0);
        long stationId = caseRow.get("station_id") instanceof Number n ? n.longValue() : 0L;
        if (stationId > 0) {
            access.requireStation(stationId);
        }

        Long reviewer = request.getAttribute("operatorUserId") instanceof Long id ? id : null;
        String actionType = body != null && body.actionType() != null ? body.actionType() : "OPERATOR_NOTE";
        String notes = body != null && body.notes() != null ? body.notes() : "Operator action logged";

        jdbc.update("""
                INSERT INTO case_action(case_id, actor_type, actor_id, action_type, notes, created_at)
                VALUES (?, 'OPERATOR', ?, ?, ?, CURRENT_TIMESTAMP(3))
                """, caseId, reviewer, actionType, notes);

        if (body != null && body.newStatus() != null && !body.newStatus().isBlank()) {
            jdbc.update("UPDATE operational_case SET status = ?, updated_at = CURRENT_TIMESTAMP(3) WHERE id = ?", body.newStatus(), caseId);
        }

        return Map.of("caseId", caseId, "actionType", actionType, "status", body != null && body.newStatus() != null ? body.newStatus() : caseRow.get("status"));
    }

    public List<Map<String, Object>> listAuditLogs(String resourceType, String resourceId, int limit) {
        int maxLimit = Math.min(Math.max(limit, 1), 200);
        if (resourceType != null && !resourceType.isBlank()) {
            return jdbc.queryForList("""
                    SELECT id, operator_user_id, actor_type, actor_id, station_id, action_code,
                           resource_type, resource_id, outcome, reason_text, occurred_at
                    FROM operation_audit_log
                    WHERE resource_type = ?
                    ORDER BY id DESC LIMIT ?
                    """, resourceType, maxLimit);
        }
        return jdbc.queryForList("""
                SELECT id, operator_user_id, actor_type, actor_id, station_id, action_code,
                       resource_type, resource_id, outcome, reason_text, occurred_at
                FROM operation_audit_log
                ORDER BY id DESC LIMIT ?
                """, maxLimit);
    }

    public record CaseActionRequest(String actionType, String notes, String newStatus) {}
}
