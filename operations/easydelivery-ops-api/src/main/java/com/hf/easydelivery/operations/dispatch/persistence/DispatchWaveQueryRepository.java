package com.hf.easydelivery.operations.dispatch.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/** Read-side projections for waves; SQL stays out of application/domain services. */
@Repository
public class DispatchWaveQueryRepository {
    private final JdbcTemplate jdbc;

    public DispatchWaveQueryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> summary(long waveId) {
        return Map.of(
                "wave", jdbc.queryForMap("SELECT id,wave_code,DATE_FORMAT(service_date, '%Y-%m-%d') AS service_date,arrival_trip_id,route_code,status,frozen_at,published_at,version FROM dispatch_wave WHERE id=?", waveId),
                "drivers", jdbc.queryForList("""
                        SELECT t.id task_id,t.task_code,t.driver_id,d.driver_name,t.status,COUNT(ti.id) parcel_count,
                               COALESCE(s.parcel_capacity, COALESCE(st.default_capacity, 200)) parcel_capacity,
                               COALESCE(s.parcel_capacity, COALESCE(st.default_capacity, 200))-COUNT(ti.id) remaining_capacity
                        FROM driver_task t JOIN driver d ON d.id=t.driver_id
                        JOIN station st ON st.id=d.home_station_id
                        LEFT JOIN driver_task_item ti ON ti.task_id=t.id AND ti.item_status='ASSIGNED'
                        LEFT JOIN driver_shift s ON s.driver_id=t.driver_id AND s.service_date=t.service_date
                        WHERE t.wave_id=? GROUP BY t.id,t.task_code,t.driver_id,d.driver_name,t.status,s.parcel_capacity,st.default_capacity ORDER BY d.driver_name
                        """, waveId),
                "areas", jdbc.queryForList("SELECT ta.task_id,ta.delivery_area_id AS delivery_area_version_id,a.area_code,ta.assignment_mode FROM driver_task_area ta JOIN delivery_area a ON a.id=ta.delivery_area_id JOIN driver_task t ON t.id=ta.task_id WHERE t.wave_id=?", waveId)
        );
    }

    public List<Map<String, Object>> list(long stationId, int limit, long afterId) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate threeDaysAgo = today.minusDays(3);
        return jdbc.queryForList("""
                SELECT w.id wave_id,w.wave_code,DATE_FORMAT(w.service_date, '%Y-%m-%d') AS service_date,w.route_code,w.status wave_status,
                       w.arrival_trip_id,COUNT(DISTINCT t.id) task_count,COUNT(ti.id) parcel_count,
                       CASE WHEN w.service_date = ? THEN 'TODAY' ELSE 'OVERDUE' END AS group_type
                FROM dispatch_wave w LEFT JOIN driver_task t ON t.wave_id=w.id
                LEFT JOIN driver_task_item ti ON ti.task_id=t.id
                WHERE w.station_id=? AND (w.service_date=? OR (w.service_date >= ? AND w.status IN ('DRAFT','FROZEN','IN_PROGRESS')) OR w.id > ?)
                GROUP BY w.id,w.wave_code,w.service_date,w.route_code,w.status,w.arrival_trip_id
                ORDER BY w.service_date DESC,w.id DESC LIMIT ?
                """, today, stationId, today, threeDaysAgo, afterId, safeLimit);
    }
}
