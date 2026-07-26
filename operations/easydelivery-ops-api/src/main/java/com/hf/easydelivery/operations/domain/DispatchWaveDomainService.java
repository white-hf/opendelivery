package com.hf.easydelivery.operations.domain;

import com.hf.easydelivery.common.domain.ParcelDomainService;
import com.hf.easydelivery.common.exception.BizException;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Domain Service for Dispatch Wave & Driver Task Aggregate operations.
 * Enforces atomic wave status transitions (DRAFT -> FROZEN -> PUBLISHED) and
 * clean driver task assignment/reassignment.
 */
@Service
@Profile("!memory")
public class DispatchWaveDomainService {

    private final JdbcTemplate jdbc;
    private final ParcelDomainService parcelDomainService;

    public DispatchWaveDomainService(JdbcTemplate jdbc, ParcelDomainService parcelDomainService) {
        this.jdbc = jdbc;
        this.parcelDomainService = parcelDomainService;
    }

    /**
     * Freeze a dispatch wave, locking its associated driver tasks.
     */
    @Transactional
    public void freezeWave(long waveId, String operatorUserId) {
        int updated = jdbc.update("""
                UPDATE dispatch_wave SET status='FROZEN', frozen_at=CURRENT_TIMESTAMP(3), frozen_by=?, version=version+1
                WHERE id=? AND status='DRAFT'
                """, operatorUserId, waveId);
        if (updated == 0) {
            throw new BizException("WAVE.STATE.INVALID", "Only a DRAFT wave can be frozen");
        }
        jdbc.update("UPDATE driver_task SET status='FROZEN', version=version+1 WHERE wave_id=?", waveId);
    }

    /**
     * Publish a dispatch wave, creating active assignments for all included parcels.
     */
    @Transactional
    public void publishWave(long waveId, String operatorUserId) {
        int updated = jdbc.update("""
                UPDATE dispatch_wave SET status='PUBLISHED', published_at=CURRENT_TIMESTAMP(3), published_by=?, version=version+1
                WHERE id=? AND status='FROZEN'
                """, operatorUserId, waveId);
        if (updated == 0) {
            throw new BizException("WAVE.STATE.INVALID", "Only a FROZEN wave can be published");
        }
        jdbc.update("UPDATE driver_task SET status='PUBLISHED', version=version+1 WHERE wave_id=?", waveId);

        // Update parcel statuses and write parcel_event logs via ParcelDomainService
        List<Long> assignedParcels = jdbc.query("""
                SELECT ti.parcel_id FROM driver_task_item ti
                JOIN driver_task t ON t.id = ti.task_id
                WHERE t.wave_id = ? AND ti.item_status = 'ASSIGNED'
                """, (rs, n) -> rs.getLong(1), waveId);

        for (Long parcelId : assignedParcels) {
            parcelDomainService.transitStatus(parcelId, "READY_FOR_DISPATCH", operatorUserId, "Published in wave #" + waveId);
        }
    }
}
