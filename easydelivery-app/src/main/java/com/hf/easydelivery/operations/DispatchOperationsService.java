package com.hf.easydelivery.operations;

import com.hf.easydelivery.common.exception.BizException;
import com.hf.easydelivery.common.store.DeliveryOperations;
import com.hf.easydelivery.config.OperationsAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.hf.easydelivery.operations.dispatch.persistence.DispatchWaveRepository;
import com.hf.easydelivery.operations.dispatch.persistence.DriverTaskRepository;

@Service
@Profile("!memory")
public class DispatchOperationsService {
    private final JdbcTemplate jdbc;
    private final OperationsAccess access;
    private final DeliveryOperations deliveryOperations;
    private final DispatchWaveRepository waveRepository;
    private final DriverTaskRepository taskRepository;

    public DispatchOperationsService(JdbcTemplate jdbc, OperationsAccess access, DeliveryOperations deliveryOperations,
                                     DispatchWaveRepository waveRepository, DriverTaskRepository taskRepository) {
        this.jdbc = jdbc;
        this.access = access;
        this.deliveryOperations = deliveryOperations;
        this.waveRepository = waveRepository;
        this.taskRepository = taskRepository;
    }

    public List<Map<String, Object>> candidates(int limit, long afterId) {
        long stationId = requireStationContext();
        // ESCAPE-HATCH (ADR-Persistence): Multi-table projection join for parcel readiness
        return jdbc.queryForList("""
                SELECT p.id parcel_id,p.tracking_no,p.status,p.promised_date,w.address_line1,w.city,w.postal_code
                FROM parcel p JOIN waybill w ON w.id=p.waybill_id
                WHERE p.current_station_id=? AND p.status='READY_FOR_DISPATCH' AND p.id > ?
                ORDER BY p.id LIMIT ?
                """, stationId, afterId, Math.min(Math.max(limit, 1), 200));
    }

    public List<Map<String, Object>> activeDrivers() {
        long stationId = requireStationContext();
        return jdbc.queryForList("SELECT id driver_id,driver_name,phone FROM driver WHERE home_station_id=? AND status='ACTIVE' ORDER BY driver_name", stationId);
    }

    public List<Map<String, Object>> waves(int limit, long afterId) {
        long stationId = requireStationContext();
        // ESCAPE-HATCH (ADR-Persistence): Set-based aggregate query for wave dispatch progress
        return jdbc.queryForList("""
                SELECT w.id wave_id,w.wave_code,w.service_date,w.route_code,w.status wave_status,
                       COUNT(DISTINCT t.id) task_count,COUNT(ti.id) parcel_count
                FROM dispatch_wave w LEFT JOIN driver_task t ON t.wave_id=w.id
                LEFT JOIN driver_task_item ti ON ti.task_id=t.id
                WHERE w.station_id=? AND w.id > ? GROUP BY w.id,w.wave_code,w.service_date,w.route_code,w.status ORDER BY w.id DESC LIMIT ?
                """, stationId, afterId, Math.min(Math.max(limit, 1), 200));
    }

    public List<Map<String, Object>> waves(LocalDate serviceDate) {
        long stationId = requireStationContext();
        return jdbc.queryForList("""
                SELECT w.id wave_id,w.wave_code,w.service_date,w.route_code,w.status wave_status,
                       COUNT(DISTINCT t.id) task_count,COUNT(ti.id) parcel_count,
                       COUNT(DISTINCT CASE WHEN t.status IN ('DRAFT','FROZEN','PUBLISHED','ACCEPTING','IN_PROGRESS') THEN t.driver_id END) driver_count
                FROM dispatch_wave w LEFT JOIN driver_task t ON t.wave_id=w.id
                LEFT JOIN driver_task_item ti ON ti.task_id=t.id AND ti.item_status IN ('ASSIGNED','LOADED','OUT_FOR_DELIVERY')
                WHERE w.station_id=? AND w.service_date=? GROUP BY w.id,w.wave_code,w.service_date,w.route_code,w.status ORDER BY w.id DESC
                """, stationId, serviceDate);
    }

    @Transactional
    public DraftResult createDraft(DraftRequest request) {
        long stationId = requireStationContext();
        if (request.trackingNumbers() == null || request.trackingNumbers().isEmpty()) {
            throw new BizException("PARCEL.LIST.EMPTY", "At least one tracking number is required");
        }
        GeneratedKeyHolder waveKey = new GeneratedKeyHolder();
        jdbc.update(c -> {
            var ps = c.prepareStatement("INSERT INTO dispatch_wave(station_id,wave_code,service_date,route_code,status) VALUES (?,?,?,?,'DRAFT')", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, stationId); ps.setString(2, request.waveCode()); ps.setObject(3, request.serviceDate()); ps.setString(4, request.routeCode()); return ps;
        }, waveKey);
        long waveId = waveKey.getKey().longValue();
        GeneratedKeyHolder taskKey = new GeneratedKeyHolder();
        jdbc.update(c -> {
            var ps = c.prepareStatement("INSERT INTO driver_task(wave_id,driver_id,station_id,task_code,service_date,status) VALUES (?,?,?,?,'DRAFT')", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, waveId); ps.setLong(2, request.driverId()); ps.setLong(3, stationId); ps.setString(4, request.waveCode() + "-D" + request.driverId()); ps.setObject(5, request.serviceDate()); return ps;
        }, taskKey);
        long taskId = taskKey.getKey().longValue();
        int seq = 1;
        for (String trackingNo : request.trackingNumbers()) {
            List<ParcelRow> parcels = jdbc.query("SELECT id,status,current_station_id,current_custody_type FROM parcel WHERE tracking_no=? FOR UPDATE", (rs, n) -> new ParcelRow(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4)), trackingNo);
            if (parcels.isEmpty()) throw new BizException("PARCEL.NOT.FOUND", "Parcel not found: " + trackingNo);
            ParcelRow parcel = parcels.get(0);
            if (parcel.stationId() != stationId || !"STATION".equals(parcel.custody()) || !"READY_FOR_DISPATCH".equals(parcel.status())) {
                throw new BizException("PARCEL.STATE.INVALID", "Parcel is not ready for dispatch: " + trackingNo);
            }
            jdbc.update("INSERT INTO driver_task_item(task_id,parcel_id,stop_sequence,item_status) VALUES (?,?,?,'ASSIGNED')", taskId, parcel.id(), seq++);
            jdbc.update("UPDATE parcel SET status='ASSIGNED',version=version+1 WHERE id=?", parcel.id());
            appendEvent(parcel.id(), "READY_FOR_DISPATCH", "ASSIGNED", "DISPATCH_ASSIGNED", "wave-assign-" + waveId + "-" + parcel.id());
        }
        return new DraftResult(waveId, taskId, request.trackingNumbers().size(), "DRAFT");
    }

    @Transactional
    public void publish(long waveId, HttpServletRequest request) {
        publishWave(waveId);
    }

    @Transactional
    public void publishWave(long waveId) {
        WaveRow wave = wave(waveId, true);
        access.requireStation(wave.stationId());
        if (!"DRAFT".equals(wave.status())) throw new BizException("WAVE.STATE.INVALID", "Only draft wave can be published");
        jdbc.update("UPDATE dispatch_wave SET status='PUBLISHED',version=version+1 WHERE id=?", waveId);
        jdbc.update("UPDATE driver_task SET status='PUBLISHED',version=version+1 WHERE wave_id=?", waveId);
    }

    @Transactional
    public void revoke(long waveId) {
        cancelWave(waveId);
    }

    @Transactional
    public void cancelWave(long waveId) {
        WaveRow wave = wave(waveId, true);
        access.requireStation(wave.stationId());
        if (!"DRAFT".equals(wave.status())) throw new BizException("WAVE.STATE.INVALID", "Only draft wave can be cancelled");
        List<Long> parcels = jdbc.query("SELECT ti.parcel_id FROM driver_task_item ti JOIN driver_task t ON t.id=ti.task_id WHERE t.wave_id=?", (rs, n) -> rs.getLong(1), waveId);
        jdbc.update("UPDATE driver_task SET status='CANCELLED',version=version+1 WHERE wave_id=?", waveId);
        jdbc.update("UPDATE dispatch_wave SET status='CANCELLED',version=version+1 WHERE id=?", waveId);
        for (Long id : parcels) {
            jdbc.update("UPDATE parcel SET status='READY_FOR_DISPATCH',version=version+1 WHERE id=? AND status='ASSIGNED'", id);
            appendEvent(id, "ASSIGNED", "READY_FOR_DISPATCH", "WAVE_REVOKED", "wave-revoke-" + waveId + "-" + id);
        }
    }

    @Transactional
    public DeliveryOperations.ScanBatch approveLoad(long sessionId, HttpServletRequest request) {
        List<Map<String, Object>> sessionRows = jdbc.queryForList("""
                SELECT s.id, s.task_id, s.driver_id, s.status, t.station_id, t.wave_id
                FROM scan_session s
                JOIN driver_task t ON t.id = s.task_id
                WHERE s.id = ? FOR UPDATE
                """, sessionId);
        if (sessionRows.isEmpty()) {
            throw new BizException("SCAN.SESSION.NOT.FOUND", "Scan session not found: " + sessionId);
        }

        Map<String, Object> sess = sessionRows.get(0);
        long stationId = ((Number) sess.get("station_id")).longValue();
        access.requireStation(stationId);

        String currentStatus = (String) sess.get("status");
        if ("APPROVED".equalsIgnoreCase(currentStatus)) {
            return deliveryOperations.reviewBatch(sessionId, "APPROVED");
        }
        if (!"SUBMITTED".equalsIgnoreCase(currentStatus)) {
            throw new BizException("SCAN.SESSION.NOT.SUBMITTED", "Load session is not awaiting review (current status: " + currentStatus + ")");
        }

        long waveId = ((Number) sess.get("wave_id")).longValue();
        Integer unsubmittedTasks = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT t.id)
                FROM driver_task t
                LEFT JOIN scan_session s ON s.task_id = t.id AND s.status IN ('SUBMITTED', 'APPROVED')
                WHERE t.wave_id = ? AND s.id IS NULL AND t.status IN ('DRAFT','FROZEN','PUBLISHED')
                """, Integer.class, waveId);
        if (unsubmittedTasks != null && unsubmittedTasks > 0) {
            throw new BizException("WAVE.SESSIONS.NOT.SUBMITTED", "All driver tasks in the wave must be submitted before approval (" + unsubmittedTasks + " unsubmitted remaining)");
        }

        Integer validCount = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT e.tracking_no)
                FROM scan_event e
                WHERE e.session_id = ? AND e.result_code = 'EXPECTED'
                """, Integer.class, sessionId);
        if (validCount == null || validCount == 0) {
            throw new BizException("SESSION.NO.VALID.SCANS", "Session has no valid expected scans and cannot be approved");
        }

        jdbc.update("""
                UPDATE driver_task_item ti
                JOIN scan_session s ON s.task_id = ti.task_id
                JOIN scan_event e ON e.session_id = s.id AND e.result_code = 'EXPECTED'
                JOIN parcel p ON p.id = ti.parcel_id AND p.tracking_no = e.tracking_no
                SET ti.item_status = 'LOADED'
                WHERE s.id = ? AND ti.item_status = 'ASSIGNED'
                """, sessionId);

        Long reviewer = request.getAttribute("operatorUserId") instanceof Long id ? id : null;
        jdbc.update("UPDATE scan_session SET reviewed_by = ? WHERE id = ?", reviewer, sessionId);

        DeliveryOperations.ScanBatch batch = deliveryOperations.reviewBatch(sessionId, "APPROVED");

        try {
            jdbc.update("INSERT INTO operation_audit_log(operator_user_id,actor_type,actor_id,station_id,action_code,resource_type,resource_id,outcome,reason_text,after_json,request_id,occurred_at) VALUES (?,'OPERATOR',?,?, 'HANDOVER_APPROVE','SCAN_SESSION',?,'SUCCESS','Load handover approved',JSON_OBJECT('sessionId',?,'validCount',?),?,CURRENT_TIMESTAMP(3))",
                    reviewer, reviewer, stationId, String.valueOf(sessionId), sessionId, validCount, request.getHeader("X-Request-Id"));
        } catch (Exception ignored) {}

        return batch;
    }

    @Transactional
    public Map<String, Object> rejectSession(long sessionId, MapPlanningService.ReasonRequest body, HttpServletRequest request) {
        List<Map<String, Object>> sessionRows = jdbc.queryForList("""
                SELECT s.id, s.task_id, s.driver_id, s.status, t.station_id
                FROM scan_session s
                JOIN driver_task t ON t.id = s.task_id
                WHERE s.id = ? FOR UPDATE
                """, sessionId);
        if (sessionRows.isEmpty()) {
            throw new BizException("SCAN.SESSION.NOT.FOUND", "Scan session not found: " + sessionId);
        }

        Map<String, Object> sess = sessionRows.get(0);
        long stationId = ((Number) sess.get("station_id")).longValue();
        access.requireStation(stationId);

        String currentStatus = (String) sess.get("status");
        if (!"SUBMITTED".equalsIgnoreCase(currentStatus)) {
            throw new BizException("SCAN.SESSION.NOT.SUBMITTED", "Only submitted sessions can be rejected back to OPEN");
        }

        Long reviewer = request.getAttribute("operatorUserId") instanceof Long id ? id : null;
        String reason = body != null && body.reason() != null ? body.reason() : "Rejected to reopen for rescan";

        jdbc.update("UPDATE scan_session SET status = 'OPEN', submitted_at = NULL, reviewed_by = ? WHERE id = ?", reviewer, sessionId);

        try {
            jdbc.update("INSERT INTO operation_audit_log(operator_user_id,actor_type,actor_id,station_id,action_code,resource_type,resource_id,outcome,reason_text,after_json,request_id,occurred_at) VALUES (?,'OPERATOR',?,?, 'HANDOVER_REJECT','SCAN_SESSION',?,'SUCCESS',?,JSON_OBJECT('sessionId',?, 'newStatus', 'OPEN'),?,CURRENT_TIMESTAMP(3))",
                    reviewer, reviewer, stationId, String.valueOf(sessionId), reason, sessionId, request.getHeader("X-Request-Id"));
        } catch (Exception ignored) {}

        return Map.of("sessionId", sessionId, "status", "OPEN", "reason", reason);
    }

    private WaveRow wave(long id, boolean lock) {
        List<WaveRow> rows = jdbc.query("SELECT w.id,w.station_id,w.status,t.id task_id FROM dispatch_wave w JOIN driver_task t ON t.wave_id=w.id WHERE w.id=?" + (lock ? " FOR UPDATE" : ""), (rs, n) -> new WaveRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getLong(4)), id);
        if (rows.isEmpty()) throw new BizException("WAVE.NOT.FOUND", "Wave not found: " + id);
        return rows.get(0);
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

    private record WaveRow(long id, long stationId, String status, long taskId) {}
    private record ParcelRow(long id, String status, long stationId, String custody) {}
    public record DraftRequest(String waveCode, LocalDate serviceDate, String routeCode, long driverId, List<String> trackingNumbers) {}
    public record DraftResult(long waveId, long taskId, int parcelCount, String status) {}
}
