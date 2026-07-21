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
        List<Long> matches = jdbc.query("""
                SELECT v.id FROM parcel p
                JOIN waybill_geocode g ON g.waybill_id=p.waybill_id
                JOIN delivery_area a ON a.station_id=? AND a.status='ACTIVE'
                JOIN delivery_area_version v ON v.delivery_area_id=a.id AND v.status='PUBLISHED'
                WHERE p.id=? AND ST_Intersects(v.boundary,g.delivery_point)
                ORDER BY a.area_level DESC,a.area_code LIMIT 1
                """, (rs, n) -> rs.getLong(1), stationId, parcelId);
        if (matches.isEmpty()) {
            return null;
        }
        long versionId = matches.get(0);
        Long current = jdbc.queryForObject("SELECT current_area_version_id FROM parcel WHERE id=?",
                Long.class, parcelId);
        if (current != null && current == versionId) {
            return versionId;
        }
        jdbc.update("UPDATE parcel_area_assignment SET ended_at=CURRENT_TIMESTAMP(3) WHERE parcel_id=? AND ended_at IS NULL",
                parcelId);
        jdbc.update("""
                INSERT INTO parcel_area_assignment(parcel_id,delivery_area_version_id,assignment_source,assignment_reason,assigned_by)
                VALUES (?,?,'GEO_POLYGON',?,?)
                """, parcelId, versionId, reason, assignedBy);
        jdbc.update("UPDATE parcel SET current_area_version_id=? WHERE id=?", versionId, parcelId);
        return versionId;
    }
}
