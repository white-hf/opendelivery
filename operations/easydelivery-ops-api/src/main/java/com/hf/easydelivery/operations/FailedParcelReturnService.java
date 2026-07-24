package com.hf.easydelivery.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.UUID;

@Service
@Profile("!memory")
public class FailedParcelReturnService {
    private final JdbcTemplate jdbc;
    private final OperationsAccess access;
    private final ObjectMapper mapper;

    public FailedParcelReturnService(JdbcTemplate jdbc, OperationsAccess access, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.access = access;
        this.mapper = mapper;
    }

    public List<Map<String, Object>> pending(LocalDate serviceDate) {
        long stationId = station();
        return jdbc.queryForList("""
                SELECT p.id parcel_id,p.tracking_no,p.status,p.current_custody_type,
                       t.id task_id,t.task_code,t.driver_id,d.driver_name,t.service_date,
                       a.failure_reason_code,a.failure_note,a.attempted_at
                FROM parcel p
                JOIN driver_task_item ti ON ti.parcel_id=p.id AND ti.item_status='FAILED'
                JOIN driver_task t ON t.id=ti.task_id AND t.station_id=?
                JOIN driver d ON d.id=t.driver_id
                LEFT JOIN delivery_attempt a ON a.id=(
                    SELECT MAX(a2.id) FROM delivery_attempt a2 WHERE a2.parcel_id=p.id)
                WHERE p.current_station_id=? AND p.status='DELIVERY_FAILED'
                  AND p.current_custody_type='DRIVER' AND t.service_date=?
                ORDER BY a.attempted_at,p.id
                """, stationId, stationId, serviceDate);
    }

    @Transactional
    public ReceiptResult receive(long parcelId, ReceiptRequest body, HttpServletRequest request) {
        String reasonCode = required(body.reasonCode(), "reasonCode").toUpperCase();
        List<Row> rows = jdbc.query("""
                SELECT p.id,p.current_station_id,p.status,p.current_custody_type,p.current_custody_id,
                       ti.id,t.id,t.driver_id,w.partner_id
                FROM parcel p
                JOIN waybill w ON w.id=p.waybill_id
                LEFT JOIN driver_task_item ti ON ti.parcel_id=p.id AND ti.item_status IN ('FAILED','RETURNED')
                LEFT JOIN driver_task t ON t.id=ti.task_id
                WHERE p.id=? FOR UPDATE
                """, (rs, n) -> new Row(rs.getLong(1), rs.getObject(2, Long.class), rs.getString(3),
                rs.getString(4), rs.getObject(5, Long.class), rs.getObject(6, Long.class),
                rs.getObject(7, Long.class), rs.getObject(8, Long.class), rs.getLong(9)), parcelId);
        if (rows.isEmpty()) throw new BizException("RETURN.PARCEL.NOT_FOUND", "Parcel not found");
        Row row = rows.get(0);
        long stationId = station();
        access.requireStation(row.stationId());
        if (FailedParcelReturnPolicy.isAlreadyReceived(row.status(), row.custodyType())) {
            return new ReceiptResult(parcelId, row.status(), row.custodyType(), true);
        }
        FailedParcelReturnPolicy.requireReceivable(row.status(), row.custodyType(), row.taskItemId(),
                row.taskId(), row.driverId(), row.custodyId());

        int changed = jdbc.update("""
                UPDATE parcel SET status='RETURNED_TO_STATION',current_custody_type='STATION',
                    current_custody_id=?,version=version+1
                WHERE id=? AND status='DELIVERY_FAILED' AND current_custody_type='DRIVER'
                """, stationId, parcelId);
        if (changed != 1) throw new BizException("RETURN.CONCURRENT_CHANGE", "Parcel changed while receiving return");
        jdbc.update("UPDATE driver_task_item SET item_status='RETURNED' WHERE id=? AND item_status='FAILED'", row.taskItemId());
        Long operatorId = operator(request);
        jdbc.update("""
                INSERT INTO custody_event(parcel_id,from_type,from_id,to_type,to_id,reason_code,
                    reference_type,reference_id,actor_id,occurred_at)
                VALUES (?,'DRIVER',?,'STATION',?,?, 'DRIVER_TASK',?,?,CURRENT_TIMESTAMP(3))
                """, parcelId, row.driverId(), stationId, reasonCode, row.taskId(), operatorId);
        long sequence = jdbc.queryForObject("SELECT COALESCE(MAX(sequence_no),0)+1 FROM parcel_status_event WHERE parcel_id=?", Long.class, parcelId);
        String eventKey = "ops-return-receipt-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO parcel_status_event(parcel_id,sequence_no,from_status,to_status,event_type,reason_code,
                    idempotency_key,actor_type,actor_id,metadata_json,occurred_at)
                VALUES (?,?,'DELIVERY_FAILED','RETURNED_TO_STATION','RETURN_RECEIVED',?,?,'OPERATOR',?,
                    JSON_OBJECT('note',?),CURRENT_TIMESTAMP(3))
                """, parcelId, sequence, reasonCode, eventKey, operatorId, body.note());
        jdbc.update("""
                INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,event_key,partner_id,payload_json)
                VALUES ('PARCEL',?,'RETURN_RECEIVED',?,?,JSON_OBJECT('parcelId',?,
                    'fromStatus','DELIVERY_FAILED','toStatus','RETURNED_TO_STATION'))
                """, parcelId, eventKey, row.partnerId(), parcelId);
        audit(request, stationId, parcelId, reasonCode, body.note(), row.driverId());
        return new ReceiptResult(parcelId, "RETURNED_TO_STATION", "STATION", false);
    }

    private void audit(HttpServletRequest request, long stationId, long parcelId, String reasonCode,
                       String note, long driverId) {
        try {
            Long operatorId = operator(request);
            jdbc.update("""
                    INSERT INTO operation_audit_log(operator_user_id,actor_type,actor_id,station_id,action_code,
                        resource_type,resource_id,outcome,reason_code,reason_text,before_json,after_json,request_id,occurred_at)
                    VALUES (?,'OPERATOR',?,?,'FAILED_PARCEL_RETURN_RECEIVED','PARCEL',?,'SUCCESS',?,?,
                        CAST(? AS JSON),CAST(? AS JSON),?,CURRENT_TIMESTAMP(3))
                    """, operatorId, operatorId, stationId, String.valueOf(parcelId), reasonCode, note,
                    mapper.writeValueAsString(Map.of("status", "DELIVERY_FAILED", "custody", "DRIVER", "driverId", driverId)),
                    mapper.writeValueAsString(Map.of("status", "RETURNED_TO_STATION", "custody", "STATION", "stationId", stationId)),
                    request.getHeader("X-Request-Id"));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to write return receipt audit", ex);
        }
    }

    private long station() {
        Long id = access.selectedStationId();
        if (id == null) throw new BizException("STATION.CONTEXT.REQUIRED", "Station context is required");
        return id;
    }

    private Long operator(HttpServletRequest request) {
        return request.getAttribute("operatorUserId") instanceof Long id ? id : null;
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new BizException("PARAM.INVALID", field + " is required");
        return value.trim();
    }

    private record Row(long parcelId, Long stationId, String status, String custodyType, Long custodyId,
                       Long taskItemId, Long taskId, Long driverId, long partnerId) {}
    public record ReceiptRequest(String reasonCode, String note) {}
    public record ReceiptResult(long parcelId, String status, String custodyType, boolean duplicate) {}
}
