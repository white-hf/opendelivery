package com.hf.easydelivery.operations.planning.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Read-side projections for planning maps and driver capacity panels. */
@Repository
public class PlanningQueryRepository {
    private final JdbcTemplate jdbc;

    public PlanningQueryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> shifts(long stationId, LocalDate serviceDate) {
        return jdbc.queryForList("""
                SELECT d.id driver_id,d.credential_id driver_code,d.driver_name,
                       s.id shift_id,COALESCE(s.availability_status,'AVAILABLE') availability_status,
                       COALESCE(s.parcel_capacity, COALESCE(st.default_capacity, 200)) parcel_capacity,
                       COUNT(DISTINCT CASE WHEN t.status IN ('DRAFT','FROZEN','PUBLISHED','ACCEPTING','IN_PROGRESS') THEN ti.id END) assigned_count
                FROM driver d JOIN station st ON st.id=d.home_station_id
                LEFT JOIN driver_shift s ON s.driver_id=d.id AND s.service_date=?
                LEFT JOIN driver_task t ON t.driver_id=d.id AND t.service_date=?
                LEFT JOIN driver_task_item ti ON ti.task_id=t.id AND ti.item_status IN ('ASSIGNED','LOADED','OUT_FOR_DELIVERY')
                WHERE d.home_station_id=? AND d.status='ACTIVE'
                GROUP BY d.id,d.credential_id,d.driver_name,s.id,s.availability_status,s.parcel_capacity,st.default_capacity
                ORDER BY d.driver_name,d.id
                """, serviceDate, serviceDate, stationId);
    }

    public List<Map<String, Object>> parcels(long stationId, LocalDate serviceDate, Double west, Double south,
                                             Double east, Double north, int limit, Long waveId, String slaFilter) {
        int safeLimit = Math.min(Math.max(limit, 1), 50000);
        boolean viewport = west != null && south != null && east != null && north != null;
        String viewportSql = viewport ? " AND ST_X(g.delivery_point) BETWEEN ? AND ? AND ST_Y(g.delivery_point) BETWEEN ? AND ?" : "";
        String waveSql = waveId != null ? " AND t.wave_id = ?" : "";
        String taskJoinSql = waveId != null
                ? "LEFT JOIN driver_task t ON t.id=ti.task_id AND t.wave_id = ? "
                : "LEFT JOIN driver_task t ON t.id=ti.task_id AND t.status <> 'CANCELLED' ";
        String slaSql = "";
        if ("TODAY_DUE".equalsIgnoreCase(slaFilter) || "EXPRESS_ONLY".equalsIgnoreCase(slaFilter)) {
            slaSql = " AND (p.promised_date <= ? OR w.service_code IN ('EXPRESS', 'SAME_DAY', 'URGENT'))";
        } else if ("STANDARD".equalsIgnoreCase(slaFilter) || "STANDARD_ONLY".equalsIgnoreCase(slaFilter)) {
            slaSql = " AND (p.promised_date > ? OR p.promised_date IS NULL) AND (w.service_code IS NULL OR w.service_code NOT IN ('EXPRESS', 'SAME_DAY', 'URGENT'))";
        }
        String sql = """
                SELECT p.id parcel_id,p.tracking_no,p.status,p.current_custody_type,p.promised_date,w.service_code,
                       w.external_waybill_no,w.recipient_name,w.address_line1,w.city,w.postal_code,
                       ST_Longitude(g.delivery_point) longitude,ST_Latitude(g.delivery_point) latitude,
                       a.area_code,a.id area_id,a.id area_version_id,t.id task_id,t.driver_id,d.driver_name,ti.stop_sequence,
                       CASE WHEN g.waybill_id IS NULL THEN 'MISSING_GEOCODE'
                            WHEN COALESCE(p.current_area_id, paa.delivery_area_id) IS NULL THEN 'UNMATCHED_AREA'
                            WHEN oc.id IS NOT NULL THEN 'OPEN_CASE' ELSE NULL END exception_code
                FROM parcel p JOIN waybill w ON w.id=p.waybill_id
                LEFT JOIN waybill_geocode g ON g.waybill_id=w.id
                LEFT JOIN parcel_area_assignment paa ON paa.parcel_id=p.id AND paa.ended_at IS NULL
                LEFT JOIN delivery_area a ON a.id=COALESCE(p.current_area_id, paa.delivery_area_id)
                LEFT JOIN driver_task_item ti ON ti.parcel_id=p.id AND ti.item_status IN ('ASSIGNED','LOADED','OUT_FOR_DELIVERY')
                """ + taskJoinSql + """
                LEFT JOIN driver d ON d.id=t.driver_id
                LEFT JOIN operational_case oc ON oc.id=(SELECT MIN(c.id) FROM operational_case c WHERE c.parcel_id=p.id AND c.status NOT IN ('RESOLVED','CLOSED'))
                WHERE p.current_station_id=? AND w.resolved_station_id=? AND w.routing_status IN ('ROUTED','OVERRIDDEN')
                  AND (p.status IN ('RECEIVED','AT_STATION','SORTED','READY_FOR_DISPATCH') OR (p.status IN ('ASSIGNED','LOADED','OUT_FOR_DELIVERY') AND t.id IS NOT NULL))
                """ + waveSql + slaSql + viewportSql + " ORDER BY p.id LIMIT ?";
        List<Object> params = new ArrayList<>();
        if (waveId != null) params.add(waveId);
        params.add(stationId); params.add(stationId);
        if (waveId != null) params.add(waveId);
        if (slaSql.length() > 0) params.add(serviceDate);
        if (viewport) { params.add(west); params.add(east); params.add(south); params.add(north); }
        params.add(safeLimit);
        return jdbc.queryForList(sql, params.toArray());
    }
}
