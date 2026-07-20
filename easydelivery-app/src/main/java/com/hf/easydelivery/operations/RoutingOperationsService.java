package com.hf.easydelivery.operations;

import com.hf.easydelivery.common.exception.BizException;
import com.hf.easydelivery.integration.routing.ShipmentRoutingService;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Profile("!memory")
public class RoutingOperationsService {
    private final JdbcTemplate jdbc;
    private final ShipmentRoutingService routing;

    public RoutingOperationsService(JdbcTemplate jdbc, ShipmentRoutingService routing) {
        this.jdbc = jdbc;
        this.routing = routing;
    }

    public List<Map<String, Object>> stations() {
        return jdbc.queryForList("""
                SELECT station_code, station_name, city, province_code, country_code, timezone, status
                FROM station ORDER BY country_code, province_code, city
                """);
    }

    @Transactional
    public long createStation(StationRequest request) {
        jdbc.update("""
                INSERT INTO station
                  (station_code, station_name, city, province_code, country_code, timezone, address_line, status)
                VALUES (UPPER(?), ?, UPPER(?), UPPER(?), UPPER(?), ?, ?, 'ACTIVE')
                """, request.stationCode(), request.stationName(), request.city(), request.provinceCode(),
                defaultCountry(request.countryCode()), request.timezone(), request.addressLine());
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public List<Map<String, Object>> serviceAreas() {
        return jdbc.queryForList("""
                SELECT a.id, s.station_code, a.country_code, a.province_code, a.city_name,
                       a.postal_prefix, a.service_code, a.priority, a.status, a.effective_from, a.effective_to
                FROM station_service_area a JOIN station s ON s.id=a.station_id ORDER BY s.station_code, a.priority DESC
                """);
    }

    @Transactional
    public long createServiceArea(ServiceAreaRequest request) {
        Long stationId = stationId(request.stationCode());
        jdbc.update("""
                INSERT INTO station_service_area
                  (station_id, country_code, province_code, city_name, postal_prefix, service_code, priority, status)
                VALUES (?, UPPER(?), UPPER(?), UPPER(?), NULLIF(UPPER(REPLACE(?, ' ', '')), ''), NULLIF(?, ''), ?, 'ACTIVE')
                """, stationId, defaultCountry(request.countryCode()), request.provinceCode(), request.cityName(),
                request.postalPrefix(), request.serviceCode(), request.priority() == null ? 100 : request.priority());
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Transactional
    public ShipmentRoutingService.RoutingDecision reroute(long waybillId) {
        requireReroutable(waybillId);
        List<WaybillAddress> rows = jdbc.query("""
                SELECT city, province, postal_code, country_code, service_code FROM waybill WHERE id=?
                """, (rs, n) -> new WaybillAddress(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5)), waybillId);
        if (rows.isEmpty()) throw new BizException("WAYBILL.NOT.FOUND", "Waybill not found: " + waybillId);
        WaybillAddress address = rows.get(0);
        ShipmentRoutingService.RoutingDecision decision = routing.route(address.city(), address.province(),
                address.postalCode(), address.countryCode(), address.serviceCode(), null);
        applyDecision(waybillId, decision, false);
        return decision;
    }

    @Transactional
    public ShipmentRoutingService.RoutingDecision override(long waybillId, RoutingOverrideRequest request) {
        requireReroutable(waybillId);
        Long stationId = stationId(request.stationCode());
        ShipmentRoutingService.RoutingDecision decision = new ShipmentRoutingService.RoutingDecision(
                "OVERRIDDEN", stationId, request.stationCode().toUpperCase(), "MANUAL_OVERRIDE");
        applyDecision(waybillId, decision, true);
        jdbc.update("""
                INSERT INTO case_action (case_id, action_type, actor_type, note, metadata_json)
                SELECT id, 'ROUTING_OVERRIDE', 'OPERATOR', ?, JSON_OBJECT('stationCode', ?)
                FROM operational_case WHERE parcel_id IN (SELECT id FROM parcel WHERE waybill_id=?)
                  AND status IN ('OPEN','ASSIGNED','IN_PROGRESS')
                """, request.reason(), request.stationCode(), waybillId);
        jdbc.update("""
                UPDATE operational_case SET status='RESOLVED', resolution_code='MANUAL_ROUTING',
                  resolution_note=?, resolved_at=CURRENT_TIMESTAMP(3)
                WHERE parcel_id IN (SELECT id FROM parcel WHERE waybill_id=?)
                  AND status IN ('OPEN','ASSIGNED','IN_PROGRESS')
                """, request.reason(), waybillId);
        return decision;
    }

    private void applyDecision(long waybillId, ShipmentRoutingService.RoutingDecision decision, boolean override) {
        int changed = jdbc.update("""
                UPDATE waybill SET routing_status=?, resolved_station_id=?, routing_reason_code=?,
                  routed_at=IF(? IN ('ROUTED','OVERRIDDEN'), CURRENT_TIMESTAMP(3), NULL), version=version+1
                WHERE id=?
                """, decision.status(), decision.stationId(), decision.reasonCode(), decision.status(), waybillId);
        if (changed == 0) throw new BizException("WAYBILL.NOT.FOUND", "Waybill not found: " + waybillId);
        jdbc.update("""
                UPDATE parcel SET current_station_id=?, status=IF(? IN ('ROUTED','OVERRIDDEN'), 'RECEIVED', 'ADDRESS_EXCEPTION'),
                  version=version+1 WHERE waybill_id=? AND status IN ('RECEIVED','ADDRESS_EXCEPTION')
                """, decision.stationId(), decision.status(), waybillId);
    }

    private void requireReroutable(long waybillId) {
        Integer locked = jdbc.queryForObject("""
                SELECT COUNT(*) FROM parcel WHERE waybill_id=?
                  AND (status NOT IN ('RECEIVED','ADDRESS_EXCEPTION') OR current_custody_type <> 'UPSTREAM')
                """, Integer.class, waybillId);
        if (locked != null && locked > 0) {
            throw new BizException("ROUTING.PHYSICAL_RECEIPT.LOCKED",
                    "Routing cannot change after station receipt or dispatch processing");
        }
    }

    private Long stationId(String stationCode) {
        List<Long> ids = jdbc.query("SELECT id FROM station WHERE station_code=? AND status='ACTIVE'",
                (rs, n) -> rs.getLong(1), stationCode.toUpperCase());
        if (ids.isEmpty()) throw new BizException("STATION.NOT.FOUND", "Active station not found: " + stationCode);
        return ids.get(0);
    }

    private String defaultCountry(String value) { return value == null || value.isBlank() ? "CA" : value; }

    private record WaybillAddress(String city, String province, String postalCode, String countryCode, String serviceCode) {}
    public record ServiceAreaRequest(String stationCode, String countryCode, String provinceCode, String cityName,
                                     String postalPrefix, String serviceCode, Integer priority) {}
    public record StationRequest(String stationCode, String stationName, String city, String provinceCode,
                                 String countryCode, String timezone, String addressLine) {}
    public record RoutingOverrideRequest(String stationCode, String reason) {}
}
