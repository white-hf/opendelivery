package com.hf.easydelivery.operations.dayclose.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Read-side projections used by the day-close screen. */
@Repository
public class DayCloseQueryRepository {
    private final JdbcTemplate jdbc;

    public DayCloseQueryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> reconciliation(long stationId, LocalDate date) {
        return jdbc.queryForList("""
                SELECT id, station_id, business_date, opening_count, inbound_count, transfer_in_count,
                       dispatched_count, driver_return_count, delivered_count, transfer_out_count,
                       upstream_return_count, expected_closing_count, actual_closing_count,
                       variance_count, open_case_count, status, carryover_reason,
                       signed_off_by, signed_off_at, created_at, updated_at
                FROM daily_reconciliation WHERE station_id = ? AND business_date = ?
                """, stationId, date);
    }

    public int inbound(long stationId, LocalDate date) {
        return value("SELECT COUNT(id) FROM parcel WHERE current_station_id = ? AND CAST(created_at AS DATE) = ?", stationId, date);
    }
    public int dispatched(long stationId, LocalDate date) {
        return value("SELECT COUNT(DISTINCT ti.parcel_id) FROM driver_task_item ti JOIN driver_task t ON t.id = ti.task_id WHERE t.station_id = ? AND t.service_date = ?", stationId, date);
    }
    public int delivered(long stationId, LocalDate date) {
        return value("SELECT COUNT(DISTINCT ti.parcel_id) FROM driver_task_item ti JOIN driver_task t ON t.id = ti.task_id JOIN parcel p ON p.id = ti.parcel_id WHERE t.station_id = ? AND t.service_date = ? AND p.status = 'DELIVERED'", stationId, date);
    }
    public int driverReturns(long stationId, LocalDate date) {
        return value("SELECT COUNT(DISTINCT ti.parcel_id) FROM driver_task_item ti JOIN driver_task t ON t.id = ti.task_id JOIN parcel p ON p.id = ti.parcel_id WHERE t.station_id = ? AND t.service_date = ? AND p.status = 'RETURNED_TO_STATION'", stationId, date);
    }
    public int openCases(long stationId) {
        return value("SELECT COUNT(id) FROM operational_case WHERE station_id = ? AND status = 'OPEN'", stationId);
    }
    public int unapprovedSessions(long stationId, LocalDate date) {
        return value("SELECT COUNT(s.id) FROM scan_session s JOIN driver_task t ON t.id = s.task_id WHERE t.station_id = ? AND t.service_date = ? AND s.status IN ('OPEN', 'SUBMITTED')", stationId, date);
    }
    private int value(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }
}
