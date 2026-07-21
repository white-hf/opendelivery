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

@Service
@Profile("!memory")
public class DeliverySupervisionService {
    private final JdbcTemplate jdbc;
    private final OperationsAccess access;

    public DeliverySupervisionService(JdbcTemplate jdbc, OperationsAccess access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    public List<Map<String, Object>> monitor(LocalDate serviceDate) {
        long stationId = requireStationContext();
        return jdbc.queryForList("""
                SELECT t.id task_id, t.task_code, d.driver_name, d.phone driver_phone,
                       COUNT(ti.id) total_assigned,
                       COUNT(CASE WHEN p.status = 'OUT_FOR_DELIVERY' THEN 1 END) out_for_delivery_count,
                       COUNT(CASE WHEN p.status = 'DELIVERED' THEN 1 END) delivered_count,
                       COUNT(CASE WHEN p.status = 'DELIVERY_FAILED' THEN 1 END) failed_count,
                       COUNT(CASE WHEN p.status = 'RETURNED_TO_STATION' THEN 1 END) returned_count,
                       COUNT(CASE WHEN p.status = 'DRIVER_HOLD_APPROVED' THEN 1 END) hold_approved_count
                FROM driver_task t
                JOIN driver d ON d.id = t.driver_id
                LEFT JOIN driver_task_item ti ON ti.task_id = t.id AND ti.item_status IN ('ASSIGNED','LOADED','OUT_FOR_DELIVERY','DELIVERED','FAILED','RETURNED')
                LEFT JOIN parcel p ON p.id = ti.parcel_id
                WHERE t.station_id = ? AND t.service_date = ?
                GROUP BY t.id, t.task_code, d.driver_name, d.phone
                ORDER BY t.id DESC
                """, stationId, serviceDate);
    }

    @Transactional
    public Map<String, Object> approveHold(long parcelId, HoldRequest body, HttpServletRequest request) {
        long stationId = requireStationContext();
        List<Map<String, Object>> parcels = jdbc.queryForList("""
                SELECT p.id, p.tracking_no, p.status, p.current_custody_type, p.current_custody_id, p.current_station_id
                FROM parcel p WHERE p.id = ? FOR UPDATE
                """, parcelId);
        if (parcels.isEmpty()) {
            throw new BizException("PARCEL.NOT.FOUND", "Parcel not found: " + parcelId);
        }
        Map<String, Object> parcel = parcels.get(0);
        String currentStatus = (String) parcel.get("status");
        if (!"DELIVERY_FAILED".equals(currentStatus) && !"OUT_FOR_DELIVERY".equals(currentStatus)) {
            throw new BizException("PARCEL.STATE.INVALID", "Only failed or out-for-delivery parcels can be approved for driver hold");
        }

        Long driverId = parcel.get("current_custody_id") instanceof Number n ? n.longValue() : 0L;
        Long reviewer = request.getAttribute("operatorUserId") instanceof Long id ? id : null;
        String reasonCode = body != null && body.reasonCode() != null ? body.reasonCode() : "CUSTOMER_UNAVAILABLE_OVERNIGHT";
        String reasonText = body != null && body.reasonText() != null ? body.reasonText() : "Approved overnight driver hold by operator";

        jdbc.update("""
                INSERT INTO driver_hold_approval (parcel_id, driver_id, station_id, approved_by, reason_code, reason_text, status)
                VALUES (?, ?, ?, ?, ?, ?, 'APPROVED')
                """, parcelId, driverId, stationId, reviewer, reasonCode, reasonText);

        jdbc.update("UPDATE parcel SET status = 'DRIVER_HOLD_APPROVED', version = version + 1 WHERE id = ?", parcelId);

        appendEvent(parcelId, currentStatus, "DRIVER_HOLD_APPROVED", "DRIVER_HOLD_APPROVED", "hold-" + parcelId);

        try {
            jdbc.update("INSERT INTO operation_audit_log(operator_user_id,actor_type,actor_id,station_id,action_code,resource_type,resource_id,outcome,reason_text,after_json,request_id,occurred_at) VALUES (?,'OPERATOR',?,?, 'APPROVE_DRIVER_HOLD','PARCEL',?,'SUCCESS',?,JSON_OBJECT('parcelId',?, 'newStatus', 'DRIVER_HOLD_APPROVED'),?,CURRENT_TIMESTAMP(3))",
                    reviewer, reviewer, stationId, String.valueOf(parcelId), reasonText, parcelId, request.getHeader("X-Request-Id"));
        } catch (Exception ignored) {}

        return Map.of("parcelId", parcelId, "status", "DRIVER_HOLD_APPROVED", "reasonCode", reasonCode);
    }

    @Transactional
    public Map<String, Object> redispatch(long parcelId, RedispatchRequest body, HttpServletRequest request) {
        long stationId = requireStationContext();
        if (body == null || body.targetTaskId() <= 0) {
            throw new BizException("REDISPATCH.TARGET_TASK.REQUIRED", "Target driver task ID is required for redispatch");
        }
        List<Map<String, Object>> parcels = jdbc.queryForList("SELECT id, status, waybill_id FROM parcel WHERE id = ? FOR UPDATE", parcelId);
        if (parcels.isEmpty()) throw new BizException("PARCEL.NOT.FOUND", "Parcel not found: " + parcelId);
        Map<String, Object> parcel = parcels.get(0);
        String currentStatus = (String) parcel.get("status");

        // Mark previous task item as REASSIGNED
        jdbc.update("UPDATE driver_task_item SET item_status = 'REASSIGNED' WHERE parcel_id = ? AND item_status IN ('ASSIGNED','FAILED')", parcelId);

        Integer maxSeq = jdbc.queryForObject("SELECT COALESCE(MAX(stop_sequence), 0) + 1 FROM driver_task_item WHERE task_id = ?", Integer.class, body.targetTaskId());
        jdbc.update("INSERT INTO driver_task_item (task_id, parcel_id, stop_sequence, item_status) VALUES (?, ?, ?, 'ASSIGNED')", body.targetTaskId(), parcelId, maxSeq);

        jdbc.update("UPDATE parcel SET status = 'ASSIGNED', version = version + 1 WHERE id = ?", parcelId);

        appendEvent(parcelId, currentStatus, "ASSIGNED", "PARCEL_REDISPATCHED", "redispatch-" + parcelId);

        return Map.of("parcelId", parcelId, "targetTaskId", body.targetTaskId(), "status", "ASSIGNED");
    }

    private Long requireStationContext() {
        Long id = access.selectedStationId();
        if (id == null) throw new BizException("STATION.CONTEXT.REQUIRED", "Station context is required");
        return id;
    }

    private void appendEvent(long parcelId, String from, String to, String type, String key) {
        Long seq = jdbc.queryForObject("SELECT COALESCE(MAX(sequence_no),0)+1 FROM parcel_status_event WHERE parcel_id=?", Long.class, parcelId);
        jdbc.update("INSERT INTO parcel_status_event(parcel_id,sequence_no,from_status,to_status,event_type,idempotency_key,actor_type,occurred_at) VALUES (?,?,?,?,?,?,'OPERATOR',CURRENT_TIMESTAMP(3))", parcelId, seq, from, to, type, key);
        Long partner = jdbc.queryForObject("SELECT w.partner_id FROM parcel p JOIN waybill w ON w.id=p.waybill_id WHERE p.id=?", Long.class, parcelId);
        jdbc.update("INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,event_key,partner_id,payload_json) VALUES ('PARCEL',?,?,?,?,JSON_OBJECT('parcelId',?,'fromStatus',?,'toStatus',?))", parcelId, type, key, partner, parcelId, from, to);
    }

    public record HoldRequest(String reasonCode, String reasonText) {}
    public record RedispatchRequest(long targetTaskId, String reason) {}
}
