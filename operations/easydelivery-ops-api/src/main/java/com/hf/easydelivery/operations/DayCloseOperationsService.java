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

@Service
@Profile("!memory")
public class DayCloseOperationsService {
    private final JdbcTemplate jdbc;
    private final OperationsAccess access;
    private final DailyReconciliationRepository reconciliationRepository;

    public DayCloseOperationsService(JdbcTemplate jdbc, OperationsAccess access, DailyReconciliationRepository reconciliationRepository) {
        this.jdbc = jdbc;
        this.access = access;
        this.reconciliationRepository = reconciliationRepository;
    }

    public Map<String, Object> getReconciliation(LocalDate serviceDate) {
        long stationId = requireStationContext();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, station_id, business_date, opening_count, inbound_count, transfer_in_count,
                       dispatched_count, driver_return_count, delivered_count, transfer_out_count,
                       upstream_return_count, expected_closing_count, actual_closing_count,
                       variance_count, open_case_count, status, carryover_reason,
                       signed_off_by, signed_off_at, created_at, updated_at
                FROM daily_reconciliation
                WHERE station_id = ? AND business_date = ?
                """, stationId, serviceDate);
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
        int inbound = jdbc.queryForObject("SELECT COUNT(id) FROM parcel WHERE current_station_id = ? AND CAST(created_at AS DATE) = ?", Integer.class, stationId, serviceDate);
        int dispatched = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT ti.parcel_id)
                FROM driver_task_item ti
                JOIN driver_task t ON t.id = ti.task_id
                WHERE t.station_id = ? AND t.service_date = ?
                """, Integer.class, stationId, serviceDate);
        int delivered = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT ti.parcel_id)
                FROM driver_task_item ti
                JOIN driver_task t ON t.id = ti.task_id
                JOIN parcel p ON p.id = ti.parcel_id
                WHERE t.station_id = ? AND t.service_date = ? AND p.status = 'DELIVERED'
                """, Integer.class, stationId, serviceDate);
        int driverReturns = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT ti.parcel_id)
                FROM driver_task_item ti
                JOIN driver_task t ON t.id = ti.task_id
                JOIN parcel p ON p.id = ti.parcel_id
                WHERE t.station_id = ? AND t.service_date = ? AND p.status = 'RETURNED_TO_STATION'
                """, Integer.class, stationId, serviceDate);
        int openCases = jdbc.queryForObject("SELECT COUNT(id) FROM operational_case WHERE station_id = ? AND status = 'OPEN'", Integer.class, stationId);
        int unapprovedSessions = jdbc.queryForObject("""
                SELECT COUNT(s.id)
                FROM scan_session s
                JOIN driver_task t ON t.id = s.task_id
                WHERE t.station_id = ? AND t.service_date = ? AND s.status IN ('OPEN', 'SUBMITTED')
                """, Integer.class, stationId, serviceDate);

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

        int unapprovedSessions = jdbc.queryForObject("""
                SELECT COUNT(s.id)
                FROM scan_session s
                JOIN driver_task t ON t.id = s.task_id
                WHERE t.station_id = ? AND t.service_date = ? AND s.status IN ('OPEN', 'SUBMITTED')
                """, Integer.class, stationId, serviceDate);
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
