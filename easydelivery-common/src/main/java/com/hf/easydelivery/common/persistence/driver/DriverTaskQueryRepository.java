package com.hf.easydelivery.common.persistence.driver;

import com.hf.easydelivery.common.dto.DeliveringListData;
import com.hf.easydelivery.common.dto.Dispatch_type;
import com.hf.easydelivery.common.store.DeliveryOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Read-side projections for the legacy Driver API contract. */
@Repository
@Profile("!memory")
public class DriverTaskQueryRepository {
    private static final DateTimeFormatter API_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final JdbcTemplate jdbc;
    public DriverTaskQueryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<DeliveringListData> unscannedParcels(int driverId) { return queryDriverParcels(driverId, "ASSIGNED", "ASSIGNED"); }
    public List<DeliveringListData> deliveringParcels(int driverId) { return queryDriverParcels(driverId, "OUT_FOR_DELIVERY", "OUT_FOR_DELIVERY"); }

    private List<DeliveringListData> queryDriverParcels(int driverId, String parcelStatus, String itemStatus) {
        return jdbc.query("""
                SELECT p.id parcel_id, p.tracking_no, p.status parcel_status, p.route_code,
                       p.current_station_id, p.updated_at, w.external_waybill_no, w.recipient_name,
                       w.recipient_phone, w.address_line1, w.address_line2, w.city, w.province,
                       w.postal_code, t.driver_id, ti.item_status, ti.stop_sequence
                FROM driver_task_item ti JOIN driver_task t ON t.id=ti.task_id
                JOIN driver d ON d.id=t.driver_id JOIN parcel p ON p.id=ti.parcel_id
                JOIN waybill w ON w.id=p.waybill_id
                WHERE t.driver_id=? AND t.status IN ('PUBLISHED','ACCEPTING','IN_PROGRESS')
                  AND p.status=? AND ti.item_status=? AND d.is_test_driver=p.is_test
                ORDER BY COALESCE(ti.stop_sequence, 2147483647), ti.id
                """, (rs, rowNum) -> mapParcel(rs.getLong("parcel_id"), rs.getString("external_waybill_no"), rs.getString("tracking_no"),
                rs.getString("route_code"), rs.getTimestamp("updated_at").toLocalDateTime(), rs.getLong("driver_id"),
                rs.getString("parcel_status"), rs.getString("recipient_name"), rs.getString("recipient_phone"),
                joinAddress(rs.getString("address_line1"), rs.getString("address_line2"), rs.getString("city"), rs.getString("province")),
                rs.getString("postal_code"), rs.getLong("current_station_id"), rs.getString("item_status"), rs.getInt("stop_sequence")), driverId, parcelStatus, itemStatus);
    }

    public DeliveringListData parcelByTrackingNo(String trackingNo) {
        List<DeliveringListData> values = jdbc.query("""
                SELECT p.id parcel_id, p.tracking_no, p.status parcel_status, p.route_code, p.current_station_id, p.updated_at,
                       w.external_waybill_no, w.recipient_name, w.recipient_phone, w.address_line1, w.address_line2, w.city, w.province,
                       w.postal_code, COALESCE(t.driver_id, 0) driver_id, COALESCE(ti.item_status, '') item_status, COALESCE(ti.stop_sequence, 0) stop_sequence
                FROM parcel p JOIN waybill w ON w.id=p.waybill_id
                LEFT JOIN driver_task_item ti ON ti.parcel_id=p.id AND ti.active_slot=1 LEFT JOIN driver_task t ON t.id=ti.task_id
                WHERE p.tracking_no=?
                """, (rs, rowNum) -> mapParcel(rs.getLong("parcel_id"), rs.getString("external_waybill_no"), rs.getString("tracking_no"), rs.getString("route_code"),
                rs.getTimestamp("updated_at").toLocalDateTime(), rs.getLong("driver_id"), rs.getString("parcel_status"), rs.getString("recipient_name"), rs.getString("recipient_phone"),
                joinAddress(rs.getString("address_line1"), rs.getString("address_line2"), rs.getString("city"), rs.getString("province")), rs.getString("postal_code"), rs.getLong("current_station_id"), rs.getString("item_status"), rs.getInt("stop_sequence")), trackingNo);
        return values.stream().findFirst().orElse(null);
    }

    public DeliveringListData parcelByOrderId(long orderId) {
        List<String> tracking = jdbc.query("SELECT tracking_no FROM parcel WHERE id=?", (rs, n) -> rs.getString(1), orderId);
        return tracking.isEmpty() ? null : parcelByTrackingNo(tracking.get(0));
    }

    public DeliveryOperations.ScanBatch batch(long batchId) {
        List<DeliveryOperations.ScanBatch> batches = jdbc.query("SELECT id, driver_id, status, opened_at FROM scan_session WHERE id=?", (rs, n) ->
                new DeliveryOperations.ScanBatch(rs.getLong("id"), rs.getInt("driver_id"), 1, 2, scanStatusCode(rs.getString("status")), scannedTracking(rs.getLong("id")), rs.getTimestamp("opened_at").toLocalDateTime().format(API_TIME)), batchId);
        return batches.stream().findFirst().orElse(null);
    }

    public List<DeliveryOperations.ScanBatch> batchesByDriver(int driverId) {
        return jdbc.query("SELECT id, driver_id, status, opened_at FROM scan_session WHERE driver_id=? ORDER BY opened_at", (rs, n) ->
                new DeliveryOperations.ScanBatch(rs.getLong("id"), rs.getInt("driver_id"), 1, 2, scanStatusCode(rs.getString("status")), scannedTracking(rs.getLong("id")), rs.getTimestamp("opened_at").toLocalDateTime().format(API_TIME)), driverId);
    }

    private List<String> scannedTracking(long sessionId) { return jdbc.query("SELECT tracking_no FROM scan_event WHERE session_id=? AND result_code='EXPECTED' ORDER BY scanned_at", (rs, n) -> rs.getString(1), sessionId); }
    private int scanStatusCode(String value) { return "APPROVED".equals(value) ? 2 : 1; }
    private DeliveringListData mapParcel(long id, String orderSn, String tracking, String route, LocalDateTime updated, long driverId, String status, String name, String phone, String address, String postalCode, long stationId, String itemStatus, int stopSequence) {
        DeliveringListData data = new DeliveringListData(); data.setOrder_id(id); data.setOrder_sn(orderSn); data.setTracking_no(tracking); data.setRoute_no(stopSequence > 0 ? stopSequence : parseRoute(route)); data.setAssign_time(updated.format(API_TIME)); data.setDelivery_by(String.valueOf(driverId)); data.setState(legacyState(status)); data.setName(name); data.setMobile(phone); data.setAddress(address); data.setZipcode(postalCode); data.setWarehouse_id((int) stationId); data.setScan_status("ASSIGNED".equals(itemStatus) ? 0 : 1); data.setStop_sequence(stopSequence); Dispatch_type dispatch = new Dispatch_type(); dispatch.setSZ(1); dispatch.setSG(2); dispatch.setDT("Regular"); dispatch.setSP(0); data.setDispatch_type(dispatch); return data;
    }
    private int parseRoute(String route) { try { return route == null ? 0 : Integer.parseInt(route); } catch (NumberFormatException ex) { return 0; } }
    private int legacyState(String status) { return "DELIVERED".equals(status) ? 3 : "OUT_FOR_DELIVERY".equals(status) ? 2 : 0; }
    private String joinAddress(String... parts) { List<String> values = new ArrayList<>(); for (String part : parts) if (part != null && !part.isBlank()) values.add(part); return String.join(", ", values); }
}
