package com.hf.easydelivery.integration.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hf.easydelivery.common.exception.BizException;
import com.hf.easydelivery.integration.ingestion.persistence.IngestionBatchEntity;
import com.hf.easydelivery.integration.ingestion.persistence.IngestionBatchRepository;
import com.hf.easydelivery.integration.ingestion.persistence.IngestionRecordEntity;
import com.hf.easydelivery.integration.ingestion.persistence.IngestionRecordRepository;
import com.hf.easydelivery.integration.ingestion.persistence.ParcelIngestionRepository;
import com.hf.easydelivery.integration.ingestion.persistence.UpstreamPartnerEntity;
import com.hf.easydelivery.integration.ingestion.persistence.UpstreamPartnerRepository;
import com.hf.easydelivery.integration.ingestion.persistence.WaybillRepository;
import com.hf.easydelivery.integration.routing.ShipmentRoutingService;
import com.hf.easydelivery.operations.shared.AreaMembershipService;
import com.hf.easydelivery.operations.arrival.ArrivalLinkagePolicy;
import com.hf.easydelivery.integration.outbox.OutboxDispatcher;
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
import java.util.Map;

@Service
@Profile("!memory")
public class ShipmentIngestionService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ShipmentRoutingService routingService;
    private final AreaMembershipService areaMembership;
    private final UpstreamPartnerRepository partnerRepository;
    private final IngestionBatchRepository batchRepository;
    private final IngestionRecordRepository recordRepository;
    private final WaybillRepository waybillRepository;
    private final ParcelIngestionRepository parcelRepository;

    public ShipmentIngestionService(JdbcTemplate jdbc, ObjectMapper objectMapper,
                                     ShipmentRoutingService routingService, AreaMembershipService areaMembership,
                                     UpstreamPartnerRepository partnerRepository, IngestionBatchRepository batchRepository,
                                     IngestionRecordRepository recordRepository, WaybillRepository waybillRepository,
                                     ParcelIngestionRepository parcelRepository) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.routingService = routingService;
        this.areaMembership = areaMembership;
        this.partnerRepository = partnerRepository;
        this.batchRepository = batchRepository;
        this.recordRepository = recordRepository;
        this.waybillRepository = waybillRepository;
        this.parcelRepository = parcelRepository;
    }

    @Transactional
    public IngestionResult ingest(String partnerCode, CanonicalShipmentRequest request) {
        UpstreamPartnerEntity partner = partnerRepository.findByPartnerCodeAndStatus(partnerCode, "ACTIVE")
                .orElseThrow(() -> new BizException("UPSTREAM.PARTNER.NOT.FOUND", "Active upstream partner not found"));
        Long partnerId = partner.getId();

        List<IngestionResult> duplicate = jdbc.query("""
                SELECT r.id, COUNT(p.id), w.routing_status, s.station_code, w.routing_reason_code
                FROM ingestion_record r
                LEFT JOIN waybill w ON w.partner_id=r.partner_id AND w.external_waybill_no=r.external_waybill_no
                LEFT JOIN parcel p ON p.waybill_id=w.id
                LEFT JOIN station s ON s.id=w.resolved_station_id
                WHERE r.partner_id=? AND r.external_event_id=?
                GROUP BY r.id, w.routing_status, s.station_code, w.routing_reason_code
                """, (rs, n) -> new IngestionResult(rs.getLong(1), true, rs.getInt(2), rs.getString(3),
                rs.getString(4), rs.getString(5)), partnerId, request.externalEventId());
        if (!duplicate.isEmpty()) return duplicate.get(0);

        ShipmentRoutingService.RoutingDecision routing = routingService.route(request.city(), request.province(),
                request.postalCode(), request.countryCode(), request.serviceCode(), request.targetStationCode());

        String payload = toJson(request);

        IngestionBatchEntity batch = new IngestionBatchEntity();
        batch.setPartnerId(partnerId);
        batch.setSourceType("PUSH");
        batch.setStatus("COMPLETED");
        batch.setReceivedCount(request.trackingNumbers().size());
        batch.setAcceptedCount(request.trackingNumbers().size());
        batch.setStartedAt(java.time.LocalDateTime.now());
        batch.setCompletedAt(java.time.LocalDateTime.now());
        batch = batchRepository.save(batch);
        long batchId = batch.getId();

        IngestionRecordEntity record = new IngestionRecordEntity();
        record.setBatchId(batchId);
        record.setPartnerId(partnerId);
        record.setExternalEventId(request.externalEventId());
        record.setExternalWaybillNo(request.externalWaybillNo());
        record.setPayloadJson(payload);
        record.setPayloadSha256(sha256(payload));
        record.setStatus("ACCEPTED");
        record.setProcessedAt(java.time.LocalDateTime.now());
        record = recordRepository.save(record);

        // ESCAPE-HATCH (ADR-Persistence): Dialect UPSERT with ON DUPLICATE KEY UPDATE retained via JdbcTemplate
        jdbc.update("""
                INSERT INTO waybill
                (partner_id, external_waybill_no, external_version, recipient_name, recipient_phone,
                 address_line1, address_line2, city, province, postal_code, country_code, service_code,
                 routing_status, resolved_station_id, routing_reason_code, routed_at,
                 delivery_window_start, delivery_window_end, source_event_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, IF(?='ROUTED', CURRENT_TIMESTAMP(3), NULL), ?, ?, CURRENT_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE external_version=VALUES(external_version), recipient_name=VALUES(recipient_name),
                  recipient_phone=VALUES(recipient_phone), address_line1=VALUES(address_line1),
                  address_line2=VALUES(address_line2), city=VALUES(city), province=VALUES(province),
                  postal_code=VALUES(postal_code), service_code=VALUES(service_code),
                  routing_status=VALUES(routing_status), resolved_station_id=VALUES(resolved_station_id),
                  routing_reason_code=VALUES(routing_reason_code), routed_at=VALUES(routed_at),
                  delivery_window_start=VALUES(delivery_window_start), delivery_window_end=VALUES(delivery_window_end),
                  source_event_time=VALUES(source_event_time), version=version+1
                """, partnerId, request.externalWaybillNo(), request.externalVersion(), request.recipientName(),
                request.recipientPhone(), request.addressLine1(), request.addressLine2(), request.city(), request.province(),
                request.postalCode(), request.countryCode() == null ? "CA" : request.countryCode(), request.serviceCode(),
                routing.status(), routing.stationId(), routing.reasonCode(), routing.status(),
                request.deliveryWindowStart(), request.deliveryWindowEnd());
        Long waybillId = waybillRepository.findByPartnerIdAndExternalWaybillNo(partnerId, request.externalWaybillNo())
                .map(com.hf.easydelivery.integration.ingestion.persistence.WaybillEntity::getId)
                .orElseThrow(() -> new BizException("WAYBILL.NOT.FOUND", "Waybill not found after upsert"));

        Map<String, String> unitLabels = ArrivalLinkagePolicy.unitLabelByTracking(request);
        if (request.deliveryLatitude() != null && request.deliveryLongitude() != null) {
            // ESCAPE-HATCH (ADR-Persistence): MySQL spatial ST_SRID function and ON DUPLICATE KEY UPDATE retained via JdbcTemplate
            jdbc.update("""
                    INSERT INTO waybill_geocode(waybill_id,delivery_point,provider_code,precision_code,confidence,normalized_address,geocoded_at)
                    VALUES (?,ST_SRID(POINT(?,?),4326),'UPSTREAM','UPSTREAM_PROVIDED',NULL,NULL,CURRENT_TIMESTAMP(3))
                    ON DUPLICATE KEY UPDATE delivery_point=VALUES(delivery_point),geocoded_at=CURRENT_TIMESTAMP(3),version=version+1
                    """, waybillId, request.deliveryLongitude(), request.deliveryLatitude());
        }

        int pieceCount = request.trackingNumbers().size();
        for (int index = 0; index < pieceCount; index++) {
            String tracking = request.trackingNumbers().get(index);
            // ESCAPE-HATCH (ADR-Persistence): Dialect UPSERT with ON DUPLICATE KEY UPDATE retained via JdbcTemplate
            jdbc.update("""
                    INSERT INTO parcel
                    (waybill_id, tracking_no, piece_no, piece_count, current_station_id, status, current_custody_type, promised_date)
                    VALUES (?, ?, ?, ?, ?, ?, 'UPSTREAM', ?)
                    ON DUPLICATE KEY UPDATE waybill_id=VALUES(waybill_id), piece_count=VALUES(piece_count),
                      current_station_id=VALUES(current_station_id), status=VALUES(status), version=version+1
                    """, waybillId, tracking, index + 1, pieceCount, routing.stationId(),
                    routing.routed() ? "RECEIVED" : "ADDRESS_EXCEPTION", request.promisedDate());
            Long parcelId = parcelRepository.findByTrackingNo(tracking)
                    .map(com.hf.easydelivery.integration.ingestion.persistence.ParcelIngestionEntity::getId)
                    .orElseThrow(() -> new BizException("PARCEL.NOT.FOUND", "Parcel not found after upsert"));
            String unitNo = unitLabels.get(tracking);
            if (unitNo != null) {
                jdbc.update("UPDATE parcel SET upstream_unit_no=? WHERE id=?", unitNo, parcelId);
                if (routing.routed()) {
                    // ESCAPE-HATCH (ADR-Persistence): Dialect INSERT IGNORE retained via JdbcTemplate
                    jdbc.update("""
                            INSERT IGNORE INTO handling_unit_parcel(handling_unit_id,parcel_id,link_source)
                            SELECT id,?,'UPSTREAM' FROM handling_unit WHERE station_id=? AND external_unit_no=?
                            """, parcelId, routing.stationId(), unitNo);
                }
            }
            if (routing.routed()) {
                areaMembership.matchFromGeocode(parcelId, routing.stationId(), "SYSTEM_INGESTION", null);
            }
            appendStatusEvent(parcelId, "INGESTION_RECEIVED", request.externalEventId() + "-" + tracking);
            if (routing.routed()) {
                appendOutboxEvent("PARCEL", parcelId, "PARCEL_RECEIVED", request.externalEventId() + "-" + tracking, partnerId,
                        Map.of("parcelId", parcelId, "trackingNo", tracking, "stationId", routing.stationId()));
            } else {
                appendOutboxEvent("PARCEL", parcelId, "ROUTING_FAILED", request.externalEventId() + "-" + tracking, partnerId,
                        Map.of("parcelId", parcelId, "trackingNo", tracking, "reasonCode", routing.reasonCode()));
            }
        }

        return new IngestionResult(record.getId(), false, pieceCount, routing.status(), routing.stationCode(), routing.reasonCode());
    }

    private Long singleId(String sql, Object arg, String code, String messageText) {
        List<Long> ids = jdbc.query(sql, (rs, n) -> rs.getLong(1), arg);
        if (ids.isEmpty()) throw new BizException(code, messageText);
        return ids.get(0);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new BizException("INGESTION.PAYLOAD.INVALID", "Failed to serialize canonical shipment payload");
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new BizException("INTERNAL_ERROR", "SHA-256 algorithm unavailable");
        }
    }

    private void appendStatusEvent(long parcelId, String eventType, String key) {
        jdbc.update("""
                INSERT INTO parcel_status_event(parcel_id, sequence_no, from_status, to_status, event_type, idempotency_key, actor_type, occurred_at)
                SELECT ?, COALESCE(MAX(sequence_no), 0) + 1, NULL, 'RECEIVED', ?, ?, 'UPSTREAM_PARTNER', CURRENT_TIMESTAMP(3)
                FROM parcel_status_event WHERE parcel_id=?
                """, parcelId, eventType, key, parcelId);
    }

    private void appendOutboxEvent(String aggregateType, long aggregateId, String eventType, String eventKey,
                                   Long partnerId, Object payload) {
        jdbc.update("""
                INSERT INTO outbox_event(aggregate_type, aggregate_id, event_type, event_key, partner_id, payload_json)
                VALUES (?, ?, ?, ?, ?, CAST(? AS JSON))
                """, aggregateType, aggregateId, eventType, eventKey, partnerId, toJson(payload));
    }

    public record IngestionResult(long recordId, boolean duplicate, int parcelCount,
                                   String routingStatus, String stationCode, String routingReasonCode) {}
}
