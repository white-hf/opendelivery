package com.hf.easydelivery.integration.routing;

import com.hf.easydelivery.common.exception.BizException;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Profile("!memory")
public class ShipmentRoutingService {
    private final JdbcTemplate jdbc;
    private final AddressNormalizer normalizer;

    public ShipmentRoutingService(JdbcTemplate jdbc, AddressNormalizer normalizer) {
        this.jdbc = jdbc;
        this.normalizer = normalizer;
    }

    public RoutingDecision route(String city, String province, String postalCode, String countryCode,
                                 String serviceCode, String upstreamStationHint) {
        AddressNormalizer.NormalizedAddress address;
        try {
            address = normalizer.normalize(city, province, postalCode, countryCode);
        } catch (BizException ex) {
            return RoutingDecision.unroutable(ex.getBizCode());
        }
        // ESCAPE-HATCH (ADR-Persistence): Complex priority ranking query joining station_service_area and station retained via JdbcTemplate
        List<Candidate> candidates = jdbc.query("""
                SELECT a.station_id, s.station_code, COALESCE(LENGTH(a.postal_prefix), 0) specificity, a.priority
                FROM station_service_area a JOIN station s ON s.id=a.station_id
                WHERE a.status='ACTIVE' AND s.status='ACTIVE'
                  AND a.country_code=? AND a.province_code=? AND a.city_name=?
                  AND (a.postal_prefix IS NULL OR ? LIKE CONCAT(a.postal_prefix, '%'))
                  AND (a.service_code IS NULL OR a.service_code=?)
                  AND (a.effective_from IS NULL OR a.effective_from <= CURRENT_TIMESTAMP(3))
                  AND (a.effective_to IS NULL OR a.effective_to > CURRENT_TIMESTAMP(3))
                ORDER BY specificity DESC, a.priority DESC, a.id ASC
                """, (rs, row) -> new Candidate(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getInt(4)),
                address.countryCode(), address.province(), address.city(), address.postalCode(), serviceCode);
        if (candidates.isEmpty()) return RoutingDecision.unroutable("NO_SERVICE_AREA");
        Candidate first = candidates.get(0);
        boolean ambiguous = candidates.stream().anyMatch(candidate -> candidate.stationId() != first.stationId()
                && candidate.specificity() == first.specificity() && candidate.priority() == first.priority());
        if (ambiguous) return RoutingDecision.ambiguous("EQUAL_PRIORITY_MATCH");
        String reason = upstreamStationHint != null && !upstreamStationHint.isBlank()
                && !upstreamStationHint.equalsIgnoreCase(first.stationCode())
                ? "RULE_MATCH_HINT_MISMATCH" : "RULE_MATCH";
        return new RoutingDecision("ROUTED", first.stationId(), first.stationCode(), reason);
    }

    private record Candidate(long stationId, String stationCode, int specificity, int priority) {}

    public record RoutingDecision(String status, Long stationId, String stationCode, String reasonCode) {
        static RoutingDecision unroutable(String reason) { return new RoutingDecision("UNROUTABLE", null, null, reason); }
        static RoutingDecision ambiguous(String reason) { return new RoutingDecision("AMBIGUOUS", null, null, reason); }
        public boolean routed() { return "ROUTED".equals(status) || "OVERRIDDEN".equals(status); }
    }
}
