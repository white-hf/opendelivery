package com.hf.easydelivery.operations.reconciliation.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Read-side SQL for driver load handover supervision. */
@Repository
public class HandoverQueryRepository {
    private final JdbcTemplate jdbc;
    public HandoverQueryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> tasks(long stationId, LocalDate date, Long waveId) {
        String sql = "SELECT w.id AS wave_id, w.wave_code, t.id AS task_id, t.driver_id, d.driver_name FROM dispatch_wave w JOIN driver_task t ON t.wave_id = w.id JOIN driver d ON d.id = t.driver_id WHERE w.station_id = ? AND w.service_date = ?" + (waveId == null ? "" : " AND w.id = ?") + " ORDER BY w.id, t.id";
        return waveId == null ? jdbc.queryForList(sql, stationId, date) : jdbc.queryForList(sql, stationId, date, waveId);
    }
    public List<Map<String, Object>> expected(long stationId, LocalDate date) {
        return jdbc.queryForList("SELECT task_id, COUNT(*) AS cnt FROM driver_task_item WHERE task_id IN (SELECT t.id FROM driver_task t JOIN dispatch_wave w ON w.id = t.wave_id WHERE w.station_id = ? AND w.service_date = ?) GROUP BY task_id", stationId, date);
    }
    public List<Map<String, Object>> eventCounts(long stationId, LocalDate date) {
        return jdbc.queryForList("SELECT s.task_id, e.result_code, COUNT(DISTINCT e.tracking_no) AS distinct_tracking, COUNT(e.id) AS cnt FROM scan_session s JOIN scan_event e ON e.session_id = s.id JOIN driver_task t ON t.id = s.task_id JOIN dispatch_wave w ON w.id = t.wave_id WHERE w.station_id = ? AND w.service_date = ? AND s.session_type = 'LOAD' GROUP BY s.task_id, e.result_code", stationId, date);
    }
    public List<Map<String, Object>> openSessions(long stationId, LocalDate date) {
        return jdbc.queryForList("SELECT s.task_id, COUNT(*) AS cnt FROM scan_session s JOIN driver_task t ON t.id = s.task_id JOIN dispatch_wave w ON w.id = t.wave_id WHERE w.station_id = ? AND w.service_date = ? AND s.status = 'OPEN' AND s.session_type = 'LOAD' GROUP BY s.task_id", stationId, date);
    }
    public boolean belongsToStation(long sessionId, long stationId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM scan_session s JOIN driver_task t ON t.id = s.task_id JOIN dispatch_wave w ON w.id = t.wave_id WHERE s.id = ? AND w.station_id = ?", Integer.class, sessionId, stationId);
        return n != null && n > 0;
    }
}
