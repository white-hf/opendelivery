package com.hf.easydelivery.operations;

import com.hf.easydelivery.common.exception.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Profile;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Service
@Profile("!memory")
public class OperationsService {
    private final JdbcTemplate jdbc;

    public OperationsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public ReceiptResult receive(String manifestNo, ManifestReceiptRequest request) {
        List<ManifestRow> rows = jdbc.query("""
                SELECT m.id manifest_id, m.station_id, mi.id item_id, p.id parcel_id, p.status
                FROM inbound_manifest m
                JOIN inbound_manifest_item mi ON mi.manifest_id=m.id
                JOIN parcel p ON p.id=mi.parcel_id
                WHERE m.external_manifest_no=? AND mi.expected_tracking_no=?
                FOR UPDATE
                """, (rs, n) -> new ManifestRow(rs.getLong("manifest_id"), rs.getLong("station_id"),
                rs.getLong("item_id"), rs.getLong("parcel_id"), rs.getString("status")), manifestNo, request.trackingNumber());
        if (rows.isEmpty()) throw new BizException("MANIFEST.ITEM.NOT.FOUND", "Tracking number is not expected on this manifest");
        ManifestRow row = rows.get(0);
        if ("AT_STATION".equals(row.status())) return new ReceiptResult(row.parcelId(), true, "AT_STATION");
        if (!"RECEIVED".equals(row.status())) throw new BizException("PARCEL.STATE.INVALID", "Parcel cannot be received from state " + row.status());

        jdbc.update("""
                UPDATE inbound_manifest_item SET receipt_status='RECEIVED', received_at=CURRENT_TIMESTAMP(3)
                WHERE id=?
                """, row.itemId());
        jdbc.update("""
                UPDATE parcel SET status='AT_STATION', current_station_id=?, current_custody_type='STATION',
                    current_custody_id=?, current_location_code='RECEIVING', version=version+1 WHERE id=?
                """, row.stationId(), row.stationId(), row.parcelId());
        jdbc.update("""
                UPDATE inbound_manifest SET status='RECEIVING', received_count=(
                    SELECT COUNT(*) FROM inbound_manifest_item WHERE manifest_id=? AND receipt_status='RECEIVED'
                ), version=version+1 WHERE id=?
                """, row.manifestId(), row.manifestId());
        jdbc.update("""
                INSERT INTO custody_event
                (parcel_id, from_type, to_type, to_id, reason_code, reference_type, reference_id, occurred_at)
                VALUES (?, 'UPSTREAM', 'STATION', ?, 'INBOUND_RECEIPT', 'MANIFEST', ?, CURRENT_TIMESTAMP(3))
                """, row.parcelId(), row.stationId(), row.manifestId());
        appendEvent(row.parcelId(), "RECEIVED", "AT_STATION", "INBOUND_RECEIPT",
                "manifest-receipt-" + row.manifestId() + "-" + row.parcelId());
        return new ReceiptResult(row.parcelId(), false, "AT_STATION");
    }

    @Transactional
    public WaveResult createAndPublishWave(CreateWaveRequest request) {
        Long stationId = requireId("SELECT id FROM station WHERE station_code=? AND status='ACTIVE'", request.stationCode(),
                "STATION.NOT.FOUND", "Station not found");
        Integer driverCount = jdbc.queryForObject("SELECT COUNT(*) FROM driver WHERE id=? AND status='ACTIVE' AND home_station_id=?",
                Integer.class, request.driverId(), stationId);
        if (driverCount == null || driverCount == 0) throw new BizException("DRIVER.NOT.AVAILABLE", "Driver is not active at this station");

        KeyHolder waveKeys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO dispatch_wave
                    (station_id, wave_code, service_date, route_code, status, published_at)
                    VALUES (?, ?, ?, ?, 'PUBLISHED', CURRENT_TIMESTAMP(3))
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, stationId); ps.setString(2, request.waveCode());
            ps.setObject(3, request.serviceDate()); ps.setString(4, request.routeCode());
            return ps;
        }, waveKeys);
        long waveId = waveKeys.getKey().longValue();

        KeyHolder taskKeys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO driver_task
                    (wave_id, driver_id, station_id, task_code, service_date, status)
                    VALUES (?, ?, ?, ?, ?, 'PUBLISHED')
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, waveId); ps.setLong(2, request.driverId()); ps.setLong(3, stationId);
            ps.setString(4, request.waveCode() + "-D" + request.driverId()); ps.setObject(5, request.serviceDate());
            return ps;
        }, taskKeys);
        long taskId = taskKeys.getKey().longValue();

        int sequence = 1;
        for (String tracking : request.trackingNumbers()) {
            List<ParcelRow> parcels = jdbc.query("""
                    SELECT id, status, current_station_id FROM parcel WHERE tracking_no=? FOR UPDATE
                    """, (rs, n) -> new ParcelRow(rs.getLong("id"), rs.getString("status"), rs.getLong("current_station_id")), tracking);
            if (parcels.isEmpty()) throw new BizException("PARCEL.NOT.FOUND", "Parcel not found: " + tracking);
            ParcelRow parcel = parcels.get(0);
            if (parcel.stationId() != stationId || !List.of("AT_STATION", "SORTED", "READY_FOR_DISPATCH").contains(parcel.status())) {
                throw new BizException("PARCEL.NOT.DISPATCHABLE", "Parcel is not dispatchable: " + tracking);
            }
            String current = parcel.status();
            if ("AT_STATION".equals(current)) {
                appendEvent(parcel.id(), current, "SORTED", "SORT_CONFIRMED", "wave-sort-" + waveId + "-" + parcel.id());
                current = "SORTED";
            }
            if ("SORTED".equals(current)) {
                appendEvent(parcel.id(), current, "READY_FOR_DISPATCH", "DISPATCH_READY", "wave-ready-" + waveId + "-" + parcel.id());
                current = "READY_FOR_DISPATCH";
            }
            appendEvent(parcel.id(), current, "ASSIGNED", "TASK_ASSIGNED", "wave-assign-" + waveId + "-" + parcel.id());
            jdbc.update("""
                    UPDATE parcel SET status='ASSIGNED', route_code=?, version=version+1 WHERE id=?
                    """, request.routeCode(), parcel.id());
            jdbc.update("""
                    INSERT INTO driver_task_item (task_id, parcel_id, stop_sequence, item_status)
                    VALUES (?, ?, ?, 'ASSIGNED')
                    """, taskId, parcel.id(), sequence++);
        }
        return new WaveResult(waveId, taskId, request.trackingNumbers().size(), "PUBLISHED");
    }

    public List<CaseSummary> openCases() {
        return jdbc.query("""
                SELECT case_no, case_type, priority, status, owner_type, owner_id, sla_due_at
                FROM operational_case WHERE status NOT IN ('RESOLVED','CLOSED') ORDER BY priority DESC, sla_due_at
                """, (rs, n) -> new CaseSummary(rs.getString("case_no"), rs.getString("case_type"),
                rs.getString("priority"), rs.getString("status"), rs.getString("owner_type"),
                rs.getObject("owner_id", Long.class), rs.getTimestamp("sla_due_at") == null ? null : rs.getTimestamp("sla_due_at").toLocalDateTime()));
    }

    private void appendEvent(long parcelId, String from, String to, String type, String key) {
        Long sequence = jdbc.queryForObject("SELECT COALESCE(MAX(sequence_no),0)+1 FROM parcel_status_event WHERE parcel_id=?", Long.class, parcelId);
        jdbc.update("""
                INSERT INTO parcel_status_event
                (parcel_id, sequence_no, from_status, to_status, event_type, idempotency_key, actor_type, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, 'OPERATOR', CURRENT_TIMESTAMP(3))
                """, parcelId, sequence, from, to, type, key);
        Long partnerId = jdbc.queryForObject("SELECT w.partner_id FROM parcel p JOIN waybill w ON w.id=p.waybill_id WHERE p.id=?", Long.class, parcelId);
        jdbc.update("""
                INSERT INTO outbox_event (aggregate_type, aggregate_id, event_type, event_key, partner_id, payload_json)
                VALUES ('PARCEL', ?, ?, ?, ?, JSON_OBJECT('parcelId', ?, 'fromStatus', ?, 'toStatus', ?))
                """, parcelId, type, key, partnerId, parcelId, from, to);
    }

    private Long requireId(String sql, String value, String code, String message) {
        List<Long> ids = jdbc.query(sql, (rs, n) -> rs.getLong(1), value);
        if (ids.isEmpty()) throw new BizException(code, message + ": " + value);
        return ids.get(0);
    }

    private record ManifestRow(long manifestId, long stationId, long itemId, long parcelId, String status) {}
    private record ParcelRow(long id, String status, long stationId) {}
    public record ReceiptResult(long parcelId, boolean duplicate, String status) {}
    public record WaveResult(long waveId, long taskId, int parcelCount, String status) {}
    public record CaseSummary(String caseNo, String caseType, String priority, String status,
                              String ownerType, Long ownerId, java.time.LocalDateTime slaDueAt) {}
}
