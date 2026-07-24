package com.hf.easydelivery.operations.shared;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Computes parcel -> delivery-area membership from the stored waybill geocode.
 * Used by ingestion (system auto-match) and by the operations bulk recompute action.
 * parcel_area_assignment stays the assignment history; parcel.current_area_version_id
 * is the denormalized projection of the active assignment and is updated in the same
 * transaction so readers can stay single-table.
 */
@Service
@Profile("!memory")
public class AreaMembershipService {
    private final JdbcTemplate jdbc;

    public AreaMembershipService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Matches one parcel to the published area containing its geocoded delivery point.
     * Idempotent: returns the active version id when already assigned to it.
     *
     * @return the matched delivery_area_version id, or null when no geocode/area matches
     */
    public Long matchFromGeocode(long parcelId, long stationId, String reason, Long assignedBy) {
        List<GeocodePoint> geocodes = jdbc.query("""
                SELECT ST_X(g.delivery_point) lng, ST_Y(g.delivery_point) lat
                FROM parcel p JOIN waybill_geocode g ON g.waybill_id=p.waybill_id
                WHERE p.id=?
                """, (rs, n) -> new GeocodePoint(rs.getDouble(1), rs.getDouble(2)), parcelId);
        if (geocodes.isEmpty()) {
            return null;
        }
        GeocodePoint point = geocodes.get(0);

        List<AreaCandidate> candidates = jdbc.query("""
                SELECT a.id, a.geojson_snapshot FROM delivery_area a
                WHERE a.station_id=? AND a.status='ACTIVE' AND a.geojson_snapshot IS NOT NULL
                ORDER BY a.area_level DESC, a.area_code
                """, (rs, n) -> new AreaCandidate(rs.getLong(1), rs.getString(2)), stationId);

        Long matchedAreaId = null;
        for (AreaCandidate candidate : candidates) {
            if (com.hf.easydelivery.common.util.JtsSpatialUtils.contains(candidate.geoJson(), point.lng(), point.lat())) {
                matchedAreaId = candidate.areaId();
                break;
            }
        }

        if (matchedAreaId == null) {
            return null;
        }
        long areaId = matchedAreaId;
        Long current = jdbc.queryForObject("SELECT current_area_id FROM parcel WHERE id=?",
                Long.class, parcelId);
        if (current != null && current == areaId) {
            return areaId;
        }
        jdbc.update("UPDATE parcel_area_assignment SET ended_at=CURRENT_TIMESTAMP(3) WHERE parcel_id=? AND ended_at IS NULL",
                parcelId);
        jdbc.update("""
                INSERT INTO parcel_area_assignment(parcel_id,delivery_area_id,assignment_source,assignment_reason,assigned_by)
                VALUES (?,?,'GEO_POLYGON',?,?)
                """, parcelId, areaId, reason, assignedBy);
        jdbc.update("UPDATE parcel SET current_area_id=? WHERE id=?", areaId, parcelId);
        return areaId;
    }

    private record GeocodePoint(double lng, double lat) {}
    private record AreaCandidate(long areaId, String geoJson) {}
}
