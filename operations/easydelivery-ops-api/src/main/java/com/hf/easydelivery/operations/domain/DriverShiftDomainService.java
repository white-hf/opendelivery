package com.hf.easydelivery.operations.domain;

import com.hf.easydelivery.common.exception.BizException;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Domain Service for Driver Shift & Capacity & Preference Aggregate.
 * Enforces capacity boundaries and automatically handles task unassignment when a driver goes UNAVAILABLE.
 */
@Service
@Profile("!memory")
public class DriverShiftDomainService {

    private final JdbcTemplate jdbc;

    public DriverShiftDomainService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Update driver shift status and capacity. If driver becomes UNAVAILABLE,
     * automatically flags assigned tasks for safety review.
     */
    @Transactional
    public void saveShift(long stationId, long driverId, LocalDate serviceDate, String availabilityStatus, int parcelCapacity, String note) {
        if (parcelCapacity < 1 || parcelCapacity > 1000) {
            throw new BizException("PARAM.INVALID", "parcelCapacity must be between 1 and 1000");
        }
        String normalizedStatus = availabilityStatus.toUpperCase().trim();
        if (!List.of("AVAILABLE", "UNAVAILABLE").contains(normalizedStatus)) {
            throw new BizException("PARAM.INVALID", "availabilityStatus must be AVAILABLE or UNAVAILABLE");
        }

        // 1. Upsert shift record
        jdbc.update("""
                INSERT INTO driver_shift(station_id, driver_id, service_date, availability_status, parcel_capacity, note)
                VALUES (?, ?, ?, ?, ?, ?) AS incoming
                ON DUPLICATE KEY UPDATE station_id=incoming.station_id, availability_status=incoming.availability_status,
                    parcel_capacity=incoming.parcel_capacity, note=incoming.note, version=driver_shift.version+1
                """, stationId, driverId, serviceDate, normalizedStatus, parcelCapacity, note);

        // 2. Cascade unassignment guard: if driver goes UNAVAILABLE, alert or reassign active draft tasks
        if ("UNAVAILABLE".equals(normalizedStatus)) {
            jdbc.update("""
                    UPDATE driver_task_item ti JOIN driver_task t ON t.id = ti.task_id
                    SET ti.item_status = 'REASSIGNED'
                    WHERE t.driver_id = ? AND t.service_date = ? AND t.status IN ('DRAFT', 'FROZEN')
                      AND ti.item_status = 'ASSIGNED'
                    """, driverId, serviceDate);
        }
    }
}
