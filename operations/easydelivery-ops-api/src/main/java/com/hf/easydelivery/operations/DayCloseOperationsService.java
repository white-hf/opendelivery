package com.hf.easydelivery.operations;

import com.hf.easydelivery.common.exception.BizException;
import com.hf.easydelivery.config.OperationsAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.hf.easydelivery.operations.dayclose.persistence.DailyReconciliationRepository;
import com.hf.easydelivery.operations.dayclose.persistence.DayCloseQueryRepository;

@Service
@Profile("!memory")
public class DayCloseOperationsService {
    private final JdbcTemplate jdbc;
    private final OperationsAccess access;
    private final DailyReconciliationRepository reconciliationRepository;
    private final DayCloseQueryRepository queryRepository;

    public DayCloseOperationsService(JdbcTemplate jdbc, OperationsAccess access, DailyReconciliationRepository reconciliationRepository, DayCloseQueryRepository queryRepository) {
        this.jdbc = jdbc;
        this.access = access;
        this.reconciliationRepository = reconciliationRepository;
        this.queryRepository = queryRepository;
    }

    public Map<String, Object> getReconciliation(LocalDate serviceDate) {
        long stationId = requireStationContext();
        List<Map<String, Object>> rows = queryRepository.reconciliation(stationId, serviceDate);
        if (!rows.isEmpty()) {
            return rows.get(0);
        }
        return Map.of(
                "stationId", stationId,
                "businessDate", serviceDate,
                "status", "OPEN",
                "varianceCount", 0,
                "openCaseCount", 0,
                "inboundCount", 0,
                "dispatchedCount", 0,
                "deliveredCount", 0
        );
    }

    @Transactional
    public Map<String, Object> recalculate(LocalDate serviceDate, HttpServletRequest request) {
        long stationId = requireStationContext();
        List<Map<String, Object>> existing = jdbc.queryForList("SELECT id, status FROM daily_reconciliation WHERE station_id = ? AND business_date = ? FOR UPDATE", stationId, serviceDate);
        if (!existing.isEmpty() && "SIGNED_OFF".equalsIgnoreCase((String) existing.get(0).get("status"))) {
            throw new BizException("DAY_CLOSE.ALREADY_SIGNED", "Reconciliation for " + serviceDate + " is already signed off and read-only");
        }

        // ESCAPE-HATCH (ADR-Persistence): Aggregating live station parcel counts & task items across domains
        int inbound = queryRepository.inbound(stationId, serviceDate);
        int dispatched = queryRepository.dispatched(stationId, serviceDate);
        int delivered = queryRepository.delivered(stationId, serviceDate);
        int driverReturns = queryRepository.driverReturns(stationId, serviceDate);
        int openCases = queryRepository.openCases(stationId);
        int unapprovedSessions = queryRepository.unapprovedSessions(stationId, serviceDate);

        int variance = unapprovedSessions;
        String newStatus = (variance == 0 && openCases == 0) ? "BALANCED" : "OPEN";

        jdbc.update("""
                INSERT INTO daily_reconciliation
                (station_id, business_date, opening_count, inbound_count, dispatched_count,
                 driver_return_count, delivered_count, expected_closing_count, actual_closing_count,
                 variance_count, open_case_count, status)
                VALUES (?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  inbound_count = VALUES(inbound_count), dispatched_count = VALUES(dispatched_count),
                  driver_return_count = VALUES(driver_return_count), delivered_count = VALUES(delivered_count),
                  variance_count = VALUES(variance_count), open_case_count = VALUES(open_case_count),
                  status = VALUES(status), updated_at = CURRENT_TIMESTAMP(3)
                """, stationId, serviceDate, inbound, dispatched, driverReturns, delivered,
                inbound - delivered, inbound - delivered, variance, openCases, newStatus);

        return getReconciliation(serviceDate);
    }

    @Transactional
    public Map<String, Object> signOff(LocalDate serviceDate, SignOffRequest body, HttpServletRequest request) {
        long stationId = requireStationContext();
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, status, variance_count, open_case_count FROM daily_reconciliation WHERE station_id = ? AND business_date = ? FOR UPDATE", stationId, serviceDate);
        if (rows.isEmpty()) {
            throw new BizException("DAY_CLOSE.NOT_CALCULATED", "Please run recalculate before signing off");
        }
        Map<String, Object> recon = rows.get(0);
        if ("SIGNED_OFF".equalsIgnoreCase((String) recon.get("status"))) {
            throw new BizException("DAY_CLOSE.ALREADY_SIGNED", "Reconciliation is already signed off");
        }

        int unapprovedSessions = queryRepository.unapprovedSessions(stationId, serviceDate);
        if (unapprovedSessions > 0) {
            throw new BizException("DAY_CLOSE.UNAPPROVED_SESSIONS", "Cannot sign off day close with " + unapprovedSessions + " unapproved scan sessions");
        }

        Long reviewer = request.getAttribute("operatorUserId") instanceof Long id ? id : null;
        String note = body != null && body.note() != null ? body.note() : "Day close signed off by operator";

        jdbc.update("""
                UPDATE daily_reconciliation
                SET status = 'SIGNED_OFF', carryover_reason = ?, signed_off_by = ?, signed_off_at = CURRENT_TIMESTAMP(3)
                WHERE station_id = ? AND business_date = ?
                """, note, reviewer, stationId, serviceDate);

        try {
            jdbc.update("INSERT INTO operation_audit_log(operator_user_id,actor_type,actor_id,station_id,action_code,resource_type,resource_id,outcome,reason_text,after_json,request_id,occurred_at) VALUES (?,'OPERATOR',?,?, 'DAY_CLOSE_SIGN','DAILY_RECONCILIATION',?,'SUCCESS',?,JSON_OBJECT('businessDate',?, 'status', 'SIGNED_OFF'),?,CURRENT_TIMESTAMP(3))",
                    reviewer, reviewer, stationId, String.valueOf(recon.get("id")), note, serviceDate.toString(), request.getHeader("X-Request-Id"));
        } catch (Exception ignored) {}

        return getReconciliation(serviceDate);
    }

    private Long requireStationContext() {
        Long id = access.selectedStationId();
        if (id == null) throw new BizException("STATION.CONTEXT.REQUIRED", "Station context is required");
        return id;
    }

    public record SignOffRequest(String note) {}
}
