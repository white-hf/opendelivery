package com.hf.easydelivery.operations.domain;

import com.hf.easydelivery.common.exception.BizException;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Domain Service for Delivery Area & Version Aggregate.
 * Manages area boundary updates and cleans/updates handling unit template master rules.
 */
@Service
@Profile("!memory")
public class DeliveryAreaDomainService {

    private final JdbcTemplate jdbc;

    public DeliveryAreaDomainService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Deactivate or archive a delivery area. Automatically cleans up active master template rules
     * in handling_unit_area_rule to prevent stale references.
     */
    @Transactional
    public void deactivateArea(long stationId, long areaId, String reason) {
        // 1. Update area status to INACTIVE
        int updated = jdbc.update("UPDATE delivery_area SET status='INACTIVE', updated_at=CURRENT_TIMESTAMP(3) WHERE id=? AND station_id=?", areaId, stationId);
        if (updated == 0) {
            throw new BizException("AREA.NOT_FOUND", "Delivery area not found or already inactive: " + areaId);
        }

        // 2. Clean up master template rules in handling_unit_area_rule
        jdbc.update("DELETE FROM handling_unit_area_rule WHERE station_id=? AND delivery_area_id=?", stationId, areaId);
    }
}
