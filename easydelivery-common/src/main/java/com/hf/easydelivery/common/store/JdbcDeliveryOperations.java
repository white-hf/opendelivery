package com.hf.easydelivery.common.store;

import com.hf.easydelivery.common.dto.DeliveringListData;
import com.hf.easydelivery.common.dto.Dispatch_type;
import com.hf.easydelivery.common.exception.BizException;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@Profile("!memory")
public class JdbcDeliveryOperations implements DeliveryOperations {
    private static final DateTimeFormatter API_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final JdbcTemplate jdbc;

    public JdbcDeliveryOperations(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<DeliveringListData> getUnscannedParcels(int driverId) {
        return queryDriverParcels(driverId, "ASSIGNED", "ASSIGNED");
    }

    @Override
    public List<DeliveringListData> getDeliveringParcels(int driverId) {
        return queryDriverParcels(driverId, "OUT_FOR_DELIVERY", "OUT_FOR_DELIVERY");
    }

    private List<DeliveringListData> queryDriverParcels(int driverId, String parcelStatus, String itemStatus) {
        return jdbc.query("""
                SELECT p.id parcel_id, p.tracking_no, p.status parcel_status, p.route_code,
                       p.current_station_id, p.updated_at, w.external_waybill_no, w.recipient_name,
                       w.recipient_phone, w.address_line1, w.address_line2, w.city, w.province,
                       w.postal_code, t.driver_id, ti.item_status
                FROM driver_task_item ti
                JOIN driver_task t ON t.id=ti.task_id
                JOIN parcel p ON p.id=ti.parcel_id
                JOIN waybill w ON w.id=p.waybill_id
                WHERE t.driver_id=? AND t.status IN ('PUBLISHED','ACCEPTING','IN_PROGRESS')
                  AND p.status=? AND ti.item_status=?
                ORDER BY COALESCE(ti.stop_sequence, 2147483647), ti.id
                """, (rs, rowNum) -> mapParcel(
                rs.getLong("parcel_id"), rs.getString("external_waybill_no"), rs.getString("tracking_no"),
                rs.getString("route_code"), rs.getTimestamp("updated_at").toLocalDateTime(),
                rs.getLong("driver_id"), rs.getString("parcel_status"), rs.getString("recipient_name"),
                rs.getString("recipient_phone"), joinAddress(rs.getString("address_line1"), rs.getString("address_line2"),
                        rs.getString("city"), rs.getString("province")), rs.getString("postal_code"),
                rs.getLong("current_station_id"), rs.getString("item_status")), driverId, parcelStatus, itemStatus);
    }

    @Override
    public DeliveringListData getParcelByTrackingNo(String trackingNo) {
        List<DeliveringListData> values = jdbc.query("""
                SELECT p.id parcel_id, p.tracking_no, p.status parcel_status, p.route_code,
                       p.current_station_id, p.updated_at, w.external_waybill_no, w.recipient_name,
                       w.recipient_phone, w.address_line1, w.address_line2, w.city, w.province,
                       w.postal_code, COALESCE(t.driver_id, 0) driver_id, COALESCE(ti.item_status, '') item_status
                FROM parcel p JOIN waybill w ON w.id=p.waybill_id
                LEFT JOIN driver_task_item ti ON ti.parcel_id=p.id AND ti.active_slot=1
                LEFT JOIN driver_task t ON t.id=ti.task_id
                WHERE p.tracking_no=?
                """, (rs, rowNum) -> mapParcel(rs.getLong("parcel_id"), rs.getString("external_waybill_no"),
                rs.getString("tracking_no"), rs.getString("route_code"), rs.getTimestamp("updated_at").toLocalDateTime(),
                rs.getLong("driver_id"), rs.getString("parcel_status"), rs.getString("recipient_name"),
                rs.getString("recipient_phone"), joinAddress(rs.getString("address_line1"), rs.getString("address_line2"),
                        rs.getString("city"), rs.getString("province")), rs.getString("postal_code"),
                rs.getLong("current_station_id"), rs.getString("item_status")), trackingNo);
        return values.stream().findFirst().orElse(null);
    }

    @Override
    public DeliveringListData getParcelByOrderId(long orderId) {
        List<String> tracking = jdbc.query("SELECT tracking_no FROM parcel WHERE id=?", (rs, n) -> rs.getString(1), orderId);
        return tracking.isEmpty() ? null : getParcelByTrackingNo(tracking.get(0));
    }

    @Override
    public void updateParcelState(long orderId, int state) {
        String status = state == 3 ? "DELIVERED" : state == 2 ? "OUT_FOR_DELIVERY" : "ASSIGNED";
        jdbc.update("UPDATE parcel SET status=?, version=version+1 WHERE id=?", status, orderId);
    }

    @Override
    @Transactional
    public long createBatch(int driverId, int operatorRole, int scanAs) {
        Long taskId = jdbc.query("""
                SELECT id FROM driver_task
                WHERE driver_id=? AND status IN ('PUBLISHED','ACCEPTING','IN_PROGRESS')
                ORDER BY service_date DESC, id DESC LIMIT 1
                """, rs -> rs.next() ? rs.getLong(1) : null, driverId);
        if (taskId == null) throw new BizException("SCAN.TASK.NOT.FOUND", "No active driver task");
        Integer expected = jdbc.queryForObject("SELECT COUNT(*) FROM driver_task_item WHERE task_id=? AND item_status='ASSIGNED'", Integer.class, taskId);
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO scan_session (task_id, driver_id, session_type, expected_count)
                    VALUES (?, ?, 'LOAD', ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, taskId);
            ps.setInt(2, driverId);
            ps.setInt(3, expected == null ? 0 : expected);
            return ps;
        }, keys);
        jdbc.update("UPDATE driver_task SET status='ACCEPTING', version=version+1 WHERE id=? AND status='PUBLISHED'", taskId);
        return keys.getKey().longValue();
    }

    @Override
    public ScanBatch getBatch(long batchId) {
        List<ScanBatch> batches = jdbc.query("""
                SELECT s.id, s.driver_id, s.status, s.opened_at
                FROM scan_session s WHERE s.id=?
                """, (rs, n) -> new ScanBatch(rs.getLong("id"), rs.getInt("driver_id"), 1, 2,
                scanStatusCode(rs.getString("status")), scannedTracking(rs.getLong("id")),
                rs.getTimestamp("opened_at").toLocalDateTime().format(API_TIME)), batchId);
        return batches.stream().findFirst().orElse(null);
    }

    @Override
    public List<ScanBatch> getAllBatchesByDriver(int driverId) {
        return jdbc.query("""
                SELECT id, driver_id, status, opened_at FROM scan_session
                WHERE driver_id=? ORDER BY opened_at
                """, (rs, n) -> new ScanBatch(rs.getLong("id"), rs.getInt("driver_id"), 1, 2,
                scanStatusCode(rs.getString("status")), scannedTracking(rs.getLong("id")),
                rs.getTimestamp("opened_at").toLocalDateTime().format(API_TIME)), driverId);
    }

    @Override
    @Transactional
    public ParcelScanResult scanParcel(String trackingNo, Long batchId, String deviceEventId) {
        if (batchId == null) return new ParcelScanResult(null, "SCAN.BATCH.REQUIRED", "scan_batch_id is required");
        ScanBatch batch = getBatch(batchId);
        if (batch == null) return new ParcelScanResult(null, "SCAN.BATCH.NOT.FOUND", "Scan batch not found");
        DeliveringListData parcel = getParcelByTrackingNo(trackingNo);
        if (parcel == null) {
            insertScanEvent(batchId, null, trackingNo, "UNKNOWN", deviceEventId);
            incrementScanCounts(batchId, true);
            return new ParcelScanResult(null, "SCAN.NOT.FOUND", "Parcel not found: " + trackingNo);
        }
        String stableDeviceEventId = deviceEventId == null || deviceEventId.isBlank() ? UUID.randomUUID().toString() : deviceEventId;
        List<String> priorResults = jdbc.query("SELECT result_code FROM scan_event WHERE device_event_id=?", (rs, n) -> rs.getString(1), stableDeviceEventId);
        if (!priorResults.isEmpty()) {
            return "EXPECTED".equals(priorResults.get(0)) ? new ParcelScanResult(parcel, null, null)
                    : new ParcelScanResult(null, "SCAN.DUPLICATE.EVENT", "Device event was already processed");
        }
        Integer duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM scan_event WHERE session_id=? AND tracking_no=?", Integer.class, batchId, trackingNo);
        if (duplicate != null && duplicate > 0) return new ParcelScanResult(null, "SCAN.ALREADY.SCANNED", "Parcel already scanned in this batch");
        Integer updated = jdbc.update("""
                UPDATE driver_task_item ti JOIN scan_session s ON s.task_id=ti.task_id
                SET ti.item_status='LOADED'
                WHERE s.id=? AND ti.parcel_id=? AND ti.item_status='ASSIGNED'
                """, batchId, parcel.getOrder_id());
        String result = updated > 0 ? "EXPECTED" : "WRONG_TASK";
        insertScanEvent(batchId, parcel.getOrder_id(), trackingNo, result, stableDeviceEventId);
        incrementScanCounts(batchId, updated == 0);
        if (updated == 0) return new ParcelScanResult(null, "SCAN.WRONG.TASK", "Parcel is not assigned to this task");
        parcel.setScan_status(1);
        return new ParcelScanResult(parcel, null, null);
    }

    @Override
    @Transactional
    public ScanBatch reviewBatch(long batchId, String status) {
        ScanBatch batch = getBatch(batchId);
        if (batch == null) return null;
        if (!"APPROVED".equalsIgnoreCase(status)) {
            jdbc.update("UPDATE scan_session SET status='REJECTED', reviewed_at=CURRENT_TIMESTAMP(3) WHERE id=?", batchId);
            return getBatch(batchId);
        }
        List<Long> transitionedParcelIds = jdbc.query("""
                SELECT ti.parcel_id FROM driver_task_item ti JOIN scan_session s ON s.task_id=ti.task_id
                WHERE s.id=? AND ti.item_status='LOADED'
                """, (rs, n) -> rs.getLong(1), batchId);
        jdbc.update("""
                UPDATE parcel p
                JOIN driver_task_item ti ON ti.parcel_id=p.id
                JOIN scan_session s ON s.task_id=ti.task_id
                SET p.status='OUT_FOR_DELIVERY', p.current_custody_type='DRIVER',
                    p.current_custody_id=s.driver_id, p.version=p.version+1,
                    ti.item_status='OUT_FOR_DELIVERY'
                WHERE s.id=? AND ti.item_status='LOADED'
                """, batchId);
        jdbc.update("""
                UPDATE driver_task t JOIN scan_session s ON s.task_id=t.id
                SET t.status='IN_PROGRESS', t.started_at=COALESCE(t.started_at,CURRENT_TIMESTAMP(3)), t.version=t.version+1
                WHERE s.id=?
                """, batchId);
        jdbc.update("UPDATE scan_session SET status='APPROVED', submitted_at=COALESCE(submitted_at,CURRENT_TIMESTAMP(3)), reviewed_at=CURRENT_TIMESTAMP(3) WHERE id=?", batchId);
        for (Long parcelId : transitionedParcelIds) appendStatusAndOutbox(parcelId, "ASSIGNED", "OUT_FOR_DELIVERY", "LOAD_HANDOVER", "scan-review-" + batchId + "-" + parcelId);
        return getBatch(batchId);
    }

    @Override
    @Transactional
    public long recordDelivery(long orderId, int authenticatedDriverId, int deliveryResult, Integer failedReason,
                               String recipientName, double latitude, double longitude, String idempotencyKey) {
        String stableKey = idempotencyKey == null || idempotencyKey.isBlank() ? "legacy-delivery-" + UUID.randomUUID() : idempotencyKey;
        List<Long> existingAttempts = jdbc.query("SELECT id FROM delivery_attempt WHERE driver_id=? AND idempotency_key=?",
                (rs, n) -> rs.getLong(1), authenticatedDriverId, stableKey);
        if (!existingAttempts.isEmpty()) return existingAttempts.get(0);
        List<Long> taskItems = jdbc.query("""
                SELECT ti.id FROM driver_task_item ti JOIN driver_task t ON t.id=ti.task_id
                WHERE ti.parcel_id=? AND t.driver_id=? AND ti.item_status='OUT_FOR_DELIVERY'
                """, (rs, n) -> rs.getLong(1), orderId, authenticatedDriverId);
        if (taskItems.isEmpty()) throw new BizException("DELIVERY.TASK.INVALID", "Parcel is not out for delivery");
        Long driverId = jdbc.queryForObject("""
                SELECT t.driver_id FROM driver_task_item ti JOIN driver_task t ON t.id=ti.task_id WHERE ti.id=?
                """, Long.class, taskItems.get(0));
        Integer attemptNo = jdbc.queryForObject("SELECT COUNT(*)+1 FROM delivery_attempt WHERE parcel_id=?", Integer.class, orderId);
        String outcome = deliveryResult == 0 ? "DELIVERED" : "FAILED";
        String key = stableKey;
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO delivery_attempt
                    (task_item_id, parcel_id, driver_id, attempt_no, outcome, failure_reason_code,
                     recipient_name, latitude, longitude, idempotency_key, attempted_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(3))
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, taskItems.get(0)); ps.setLong(2, orderId); ps.setLong(3, driverId);
            ps.setInt(4, attemptNo == null ? 1 : attemptNo); ps.setString(5, outcome);
            ps.setString(6, failedReason == null ? null : String.valueOf(failedReason));
            ps.setString(7, recipientName); ps.setDouble(8, latitude); ps.setDouble(9, longitude); ps.setString(10, key);
            return ps;
        }, keys);
        String target = deliveryResult == 0 ? "DELIVERED" : "DELIVERY_FAILED";
        String itemStatus = deliveryResult == 0 ? "DELIVERED" : "FAILED";
        jdbc.update("UPDATE parcel SET status=?, version=version+1 WHERE id=? AND status='OUT_FOR_DELIVERY'", target, orderId);
        jdbc.update("UPDATE driver_task_item SET item_status=? WHERE id=?", itemStatus, taskItems.get(0));
        if (recipientName != null && !recipientName.isBlank()) {
            jdbc.update("""
                    INSERT INTO proof_of_delivery (attempt_id, pod_type, captured_at, metadata_json)
                    VALUES (?, 'RECIPIENT', CURRENT_TIMESTAMP(3), JSON_OBJECT('recipientName', ?))
                    """, keys.getKey().longValue(), recipientName);
        }
        appendStatusAndOutbox(orderId, "OUT_FOR_DELIVERY", target, outcome, key);
        return keys.getKey().longValue();
    }

    @Override
    public void recordPod(long attemptId, String podType, String objectUri, String sha256,
                          String contentType, long contentSize) {
        jdbc.update("""
                INSERT INTO proof_of_delivery
                (attempt_id, pod_type, object_uri, content_sha256, content_type, content_size, captured_at)
                SELECT ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(3)
                WHERE NOT EXISTS (
                    SELECT 1 FROM proof_of_delivery WHERE attempt_id=? AND content_sha256=? AND pod_type=?
                )
                """, attemptId, podType, objectUri, sha256, contentType, contentSize, attemptId, sha256, podType);
    }

    @Override
    @Transactional
    public void retryDelivery(long orderId, int authenticatedDriverId) {
        Integer updated = jdbc.update("""
                UPDATE parcel p JOIN driver_task_item ti ON ti.parcel_id=p.id
                JOIN driver_task t ON t.id=ti.task_id
                SET p.status='OUT_FOR_DELIVERY', p.version=p.version+1, ti.item_status='OUT_FOR_DELIVERY'
                WHERE p.id=? AND t.driver_id=? AND p.status='DELIVERY_FAILED' AND ti.item_status='FAILED'
                """, orderId, authenticatedDriverId);
        if (updated == 0) throw new BizException("DELIVERY.RETRY.INVALID", "Parcel is not retryable by this driver");
        appendStatusAndOutbox(orderId, "DELIVERY_FAILED", "OUT_FOR_DELIVERY", "DELIVERY_RETRY", "legacy-retry-" + UUID.randomUUID());
    }

    private void insertScanEvent(long batchId, Long parcelId, String trackingNo, String result, String deviceEventId) {
        jdbc.update("""
                INSERT INTO scan_event (session_id, parcel_id, tracking_no, device_event_id, result_code, scanned_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP(3))
                """, batchId, parcelId, trackingNo,
                deviceEventId == null || deviceEventId.isBlank() ? UUID.randomUUID().toString() : deviceEventId, result);
    }

    private void incrementScanCounts(long batchId, boolean discrepancy) {
        jdbc.update("""
                UPDATE scan_session SET scanned_count=scanned_count+1,
                    discrepancy_count=discrepancy_count+? WHERE id=?
                """, discrepancy ? 1 : 0, batchId);
    }

    private List<String> scannedTracking(long sessionId) {
        return jdbc.query("SELECT tracking_no FROM scan_event WHERE session_id=? AND result_code='EXPECTED' ORDER BY scanned_at",
                (rs, n) -> rs.getString(1), sessionId);
    }

    private int scanStatusCode(String value) {
        return "APPROVED".equals(value) ? 2 : 1;
    }

    private void appendStatusAndOutbox(long parcelId, String from, String to, String eventType, String key) {
        Long sequence = jdbc.queryForObject("SELECT COALESCE(MAX(sequence_no),0)+1 FROM parcel_status_event WHERE parcel_id=?", Long.class, parcelId);
        jdbc.update("""
                INSERT INTO parcel_status_event
                (parcel_id, sequence_no, from_status, to_status, event_type, idempotency_key, actor_type, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, 'SYSTEM', CURRENT_TIMESTAMP(3))
                """, parcelId, sequence, from, to, eventType, key);
        Long partnerId = jdbc.queryForObject("""
                SELECT w.partner_id FROM parcel p JOIN waybill w ON w.id=p.waybill_id WHERE p.id=?
                """, Long.class, parcelId);
        jdbc.update("""
                INSERT INTO outbox_event (aggregate_type, aggregate_id, event_type, event_key, partner_id, payload_json)
                VALUES ('PARCEL', ?, ?, ?, ?, JSON_OBJECT('parcelId', ?, 'fromStatus', ?, 'toStatus', ?))
                """, parcelId, eventType, key, partnerId, parcelId, from, to);
    }

    private DeliveringListData mapParcel(long id, String orderSn, String tracking, String route,
                                         LocalDateTime updated, long driverId, String status, String name,
                                         String phone, String address, String postalCode, long stationId, String itemStatus) {
        DeliveringListData data = new DeliveringListData();
        data.setOrder_id(id); data.setOrder_sn(orderSn); data.setTracking_no(tracking);
        data.setGoods_type(1); data.setExpress_type(1); data.setRoute_no(parseRoute(route));
        data.setAssign_time(updated.format(API_TIME)); data.setDelivery_by(String.valueOf(driverId));
        data.setState(legacyState(status)); data.setName(name); data.setMobile(phone);
        data.setAddress(address); data.setZipcode(postalCode); data.setWarehouse_id((int) stationId);
        data.setScan_status("ASSIGNED".equals(itemStatus) ? 0 : 1);
        Dispatch_type dispatch = new Dispatch_type(); dispatch.setSZ(1); dispatch.setSG(2); dispatch.setDT("Regular"); dispatch.setSP(0);
        data.setDispatch_type(dispatch);
        return data;
    }

    private int parseRoute(String route) {
        try { return route == null ? 0 : Integer.parseInt(route); } catch (NumberFormatException ex) { return 0; }
    }

    private int legacyState(String status) {
        return "DELIVERED".equals(status) ? 3 : "OUT_FOR_DELIVERY".equals(status) ? 2 : 0;
    }

    private String joinAddress(String... parts) {
        List<String> values = new ArrayList<>();
        for (String part : parts) if (part != null && !part.isBlank()) values.add(part);
        return String.join(", ", values);
    }
}
