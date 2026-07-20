package com.hf.easydelivery.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hf.easydelivery.common.exception.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Profile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.List;

@Service
@Profile("!memory")
public class ShipmentIngestionService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ShipmentIngestionService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public IngestionResult ingest(String partnerCode, CanonicalShipmentRequest request) {
        Long partnerId = singleId("SELECT id FROM upstream_partner WHERE partner_code=? AND status='ACTIVE'", partnerCode,
                "UPSTREAM.PARTNER.NOT.FOUND", "Active upstream partner not found");
        Long stationId = singleId("SELECT id FROM station WHERE station_code=? AND status='ACTIVE'", request.targetStationCode(),
                "STATION.NOT.FOUND", "Active target station not found");

        List<Long> duplicate = jdbc.query("""
                SELECT id FROM ingestion_record WHERE partner_id=? AND external_event_id=?
                """, (rs, n) -> rs.getLong(1), partnerId, request.externalEventId());
        if (!duplicate.isEmpty()) return new IngestionResult(duplicate.get(0), true, request.trackingNumbers().size());

        String payload = toJson(request);
        KeyHolder batchKeys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO ingestion_batch
                    (partner_id, source_type, status, received_count, accepted_count, completed_at)
                    VALUES (?, 'PUSH', 'COMPLETED', ?, ?, CURRENT_TIMESTAMP(3))
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, partnerId); ps.setInt(2, request.trackingNumbers().size()); ps.setInt(3, request.trackingNumbers().size());
            return ps;
        }, batchKeys);
        long batchId = batchKeys.getKey().longValue();

        KeyHolder recordKeys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO ingestion_record
                    (batch_id, partner_id, external_event_id, external_waybill_no, payload_json,
                     payload_sha256, status, processed_at)
                    VALUES (?, ?, ?, ?, CAST(? AS JSON), ?, 'ACCEPTED', CURRENT_TIMESTAMP(3))
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, batchId); ps.setLong(2, partnerId); ps.setString(3, request.externalEventId());
            ps.setString(4, request.externalWaybillNo()); ps.setString(5, payload); ps.setString(6, sha256(payload));
            return ps;
        }, recordKeys);

        jdbc.update("""
                INSERT INTO waybill
                (partner_id, external_waybill_no, external_version, recipient_name, recipient_phone,
                 address_line1, address_line2, city, province, postal_code, country_code, service_code,
                 delivery_window_start, delivery_window_end, source_event_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE external_version=VALUES(external_version), recipient_name=VALUES(recipient_name),
                  recipient_phone=VALUES(recipient_phone), address_line1=VALUES(address_line1),
                  address_line2=VALUES(address_line2), city=VALUES(city), province=VALUES(province),
                  postal_code=VALUES(postal_code), service_code=VALUES(service_code),
                  delivery_window_start=VALUES(delivery_window_start), delivery_window_end=VALUES(delivery_window_end),
                  source_event_time=VALUES(source_event_time), version=version+1
                """, partnerId, request.externalWaybillNo(), request.externalVersion(), request.recipientName(),
                request.recipientPhone(), request.addressLine1(), request.addressLine2(), request.city(), request.province(),
                request.postalCode(), request.countryCode() == null ? "CA" : request.countryCode(), request.serviceCode(),
                request.deliveryWindowStart(), request.deliveryWindowEnd());
        Long waybillId = jdbc.queryForObject("SELECT id FROM waybill WHERE partner_id=? AND external_waybill_no=?",
                Long.class, partnerId, request.externalWaybillNo());

        int pieceCount = request.trackingNumbers().size();
        for (int index = 0; index < pieceCount; index++) {
            String tracking = request.trackingNumbers().get(index);
            jdbc.update("""
                    INSERT INTO parcel
                    (waybill_id, tracking_no, piece_no, piece_count, status, current_custody_type, promised_date)
                    VALUES (?, ?, ?, ?, 'RECEIVED', 'UPSTREAM', ?)
                    ON DUPLICATE KEY UPDATE waybill_id=VALUES(waybill_id), piece_count=VALUES(piece_count), version=version+1
                    """, waybillId, tracking, index + 1, pieceCount, request.promisedDate());
            Long parcelId = jdbc.queryForObject("SELECT id FROM parcel WHERE tracking_no=?", Long.class, tracking);
            String eventKey = "ingestion-" + request.externalEventId() + "-" + tracking;
            jdbc.update("""
                    INSERT IGNORE INTO parcel_status_event
                    (parcel_id, sequence_no, from_status, to_status, event_type, idempotency_key, actor_type, occurred_at)
                    VALUES (?, 1, NULL, 'RECEIVED', 'UPSTREAM_RECEIVED', ?, 'UPSTREAM', CURRENT_TIMESTAMP(3))
                    """, parcelId, eventKey);
        }

        if (request.externalManifestNo() != null && !request.externalManifestNo().isBlank()) {
            jdbc.update("""
                    INSERT INTO inbound_manifest
                    (partner_id, station_id, external_manifest_no, status, expected_count)
                    VALUES (?, ?, ?, 'EXPECTED', ?)
                    ON DUPLICATE KEY UPDATE expected_count=VALUES(expected_count), version=version+1
                    """, partnerId, stationId, request.externalManifestNo(), pieceCount);
            Long manifestId = jdbc.queryForObject("SELECT id FROM inbound_manifest WHERE partner_id=? AND external_manifest_no=?",
                    Long.class, partnerId, request.externalManifestNo());
            for (String tracking : request.trackingNumbers()) {
                Long parcelId = jdbc.queryForObject("SELECT id FROM parcel WHERE tracking_no=?", Long.class, tracking);
                jdbc.update("""
                        INSERT INTO inbound_manifest_item (manifest_id, parcel_id, expected_tracking_no)
                        VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE parcel_id=VALUES(parcel_id)
                        """, manifestId, parcelId, tracking);
            }
        }
        return new IngestionResult(recordKeys.getKey().longValue(), false, pieceCount);
    }

    private Long singleId(String sql, String value, String code, String message) {
        List<Long> ids = jdbc.query(sql, (rs, n) -> rs.getLong(1), value);
        if (ids.isEmpty()) throw new BizException(code, message + ": " + value);
        return ids.get(0);
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("Cannot serialize ingestion request", ex); }
    }

    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    public record IngestionResult(long ingestionRecordId, boolean duplicate, int parcelCount) {}
}
