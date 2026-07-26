package com.hf.easydelivery.operations.domain;

import com.hf.easydelivery.common.exception.BizException;
import com.hf.easydelivery.operations.arrival.persistence.HandlingUnitEntity;
import com.hf.easydelivery.operations.arrival.persistence.HandlingUnitRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Rich Domain Service encapsulating all Handling Unit (HU) aggregate operations.
 * Enforces atomic overwrite, template rule updates, and clean unbinding across modules.
 */
@Service
@Profile("!memory")
public class HandlingUnitDomainService {

    private final HandlingUnitRepository unitRepo;
    private final JdbcTemplate jdbc;

    public HandlingUnitDomainService(HandlingUnitRepository unitRepo, JdbcTemplate jdbc) {
        this.unitRepo = unitRepo;
        this.jdbc = jdbc;
    }

    /**
     * Atomically reassigns delivery areas to a target Handling Unit (HU).
     * Clears old area bindings on current/other units to prevent stale redundant data.
     */
    @Transactional
    public int reassignAreasToUnit(long unitId, List<Long> areaVersionIds, String reason) {
        HandlingUnitEntity unit = unitRepo.findByIdForUpdate(unitId)
                .orElseThrow(() -> new BizException("ARRIVAL.UNIT.NOT_FOUND", "Handling unit not found"));

        // 1. If empty list provided, clear all area associations for this unit
        if (areaVersionIds == null || areaVersionIds.isEmpty()) {
            jdbc.update("DELETE FROM handling_unit_parcel WHERE handling_unit_id=? AND link_source='AREA_PLAN'", unit.getId());
            jdbc.update("DELETE FROM handling_unit_area_rule WHERE station_id=? AND unit_code=?", unit.getStationId(), unit.getExternalUnitNo());
            return 0;
        }

        // 2. Validate all requested areas belong to station
        for (Long areaId : areaVersionIds) {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM delivery_area a WHERE a.id=? AND a.station_id=? AND a.status='ACTIVE'", Integer.class, areaId, unit.getStationId());
            if (n == null || n == 0) {
                throw new BizException("AREA.NOT.AVAILABLE", "Active area does not belong to selected station: " + areaId);
            }
        }

        // 3. Clear existing AREA_PLAN bindings for this unit first
        jdbc.update("DELETE FROM handling_unit_parcel WHERE handling_unit_id=? AND link_source='AREA_PLAN'", unit.getId());
        jdbc.update("DELETE FROM handling_unit_area_rule WHERE station_id=? AND unit_code=?", unit.getStationId(), unit.getExternalUnitNo());

        int totalLinked = 0;
        for (Long areaId : areaVersionIds) {
            // 4. Update persistent master template
            jdbc.update("INSERT IGNORE INTO handling_unit_area_rule(station_id,unit_code,delivery_area_id) VALUES (?,?,?)",
                    unit.getStationId(), unit.getExternalUnitNo(), areaId);

            // 5. Materialize to active parcel linkages for current trip (atomic conflict prevention)
            totalLinked += jdbc.update("""
                    INSERT IGNORE INTO handling_unit_parcel(handling_unit_id,parcel_id,link_source)
                    SELECT ?,p.id,'AREA_PLAN' FROM parcel p
                    WHERE p.current_area_id=? AND p.current_station_id=?
                      AND p.status NOT IN ('DELIVERED','RETURNED_TO_UPSTREAM','CANCELLED','LOST')
                      AND NOT EXISTS(SELECT 1 FROM handling_unit_parcel other JOIN handling_unit ou ON ou.id=other.handling_unit_id
                                     WHERE other.parcel_id=p.id AND ou.trip_id=? AND other.handling_unit_id<>?)
                    """, unit.getId(), areaId, unit.getStationId(), unit.getTripId(), unit.getId());
        }

        return totalLinked;
    }

    /**
     * Unified read-only projection view for handling unit parcel mapping.
     */
    public List<Map<String, Object>> getUnitParcelMappingsByTrip(long tripId) {
        return jdbc.queryForList("""
                SELECT hp.handling_unit_id unit_id, p.id parcel_id, p.tracking_no, p.status parcel_status, hp.link_source
                FROM handling_unit_parcel hp
                JOIN parcel p ON p.id = hp.parcel_id
                JOIN handling_unit hu ON hu.id = hp.handling_unit_id
                WHERE hu.trip_id = ? ORDER BY hp.handling_unit_id, p.tracking_no
                """, tripId);
    }
}
