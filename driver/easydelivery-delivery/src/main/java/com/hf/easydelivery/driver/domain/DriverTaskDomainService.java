package com.hf.easydelivery.driver.domain;

import com.hf.easydelivery.common.domain.ParcelDomainService;
import com.hf.easydelivery.common.exception.BizException;


import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Domain Service for Driver Execution, Scan Verification, and Delivery POD Lifecycle.
 * Connects Driver App operations directly to ParcelDomainService for atomic state machine transitions and audit logging.
 */
@Service
@Profile("!memory")
public class DriverTaskDomainService {

    private final JdbcTemplate jdbc;
    private final ParcelDomainService parcelDomainService;

    public DriverTaskDomainService(JdbcTemplate jdbc, ParcelDomainService parcelDomainService) {
        this.jdbc = jdbc;
        this.parcelDomainService = parcelDomainService;
    }

    /**
     * Confirms scan verification for a batch of parcels, advancing status from READY_FOR_DISPATCH -> OUT_FOR_DELIVERY.
     */
    @Transactional
    public void confirmScanBatch(long driverId, List<Long> parcelIds, String deviceEventId) {
        if (parcelIds == null || parcelIds.isEmpty()) {
            return;
        }

        for (Long parcelId : parcelIds) {
            // Verify parcel is assigned to this driver's active task
            List<Long> tasks = jdbc.query("""
                    SELECT ti.task_id FROM driver_task_item ti
                    JOIN driver_task t ON t.id = ti.task_id
                    WHERE t.driver_id = ? AND ti.parcel_id = ? AND ti.item_status = 'ASSIGNED'
                    """, (rs, n) -> rs.getLong(1), driverId, parcelId);

            if (tasks.isEmpty()) {
                throw new BizException("DRIVER.PARCEL.NOT_ASSIGNED", "Parcel #" + parcelId + " is not assigned to driver #" + driverId);
            }

            // Update item status in driver_task_item
            jdbc.update("UPDATE driver_task_item SET item_status='OUT_FOR_DELIVERY' WHERE task_id=? AND parcel_id=?", tasks.get(0), parcelId);

            // Advance parcel status machine & write parcel_event audit log
            parcelDomainService.transitStatus(parcelId, "OUT_FOR_DELIVERY", String.valueOf(driverId), "Driver scan verified via event " + deviceEventId);
        }
    }

    /**
     * Record proof-of-delivery (POD) or delivery attempt failure.
     * Advances status machine to DELIVERED or FAILED and logs audit event.
     */
    @Transactional
    public void completeDeliveryAttempt(long driverId, long orderId, int deliveryResult, Integer failedReason, String recipientName, String idempotencyKey) {
        // Fetch parcel associated with order
        List<Long> parcelIds = jdbc.query("SELECT id FROM parcel WHERE waybill_id = ?", (rs, n) -> rs.getLong(1), orderId);
        if (parcelIds.isEmpty()) {
            throw new BizException("DELIVERY.ORDER.NOT_FOUND", "No parcel found for order ID: " + orderId);
        }
        long parcelId = parcelIds.get(0);

        String targetStatus = (deliveryResult == 0) ? "DELIVERED" : "FAILED";
        String note = (deliveryResult == 0) ? "Delivered to " + (recipientName != null ? recipientName : "recipient")
                : "Attempt failed, reason code: " + failedReason;

        // Record attempt history
        jdbc.update("""
                INSERT INTO delivery_attempt(parcel_id, driver_id, attempt_status, failed_reason_code, recipient_name, idempotency_key, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(3))
                """, parcelId, driverId, targetStatus, failedReason, recipientName, idempotencyKey);

        // Update driver task item status
        jdbc.update("""
                UPDATE driver_task_item ti JOIN driver_task t ON t.id = ti.task_id
                SET ti.item_status = ? WHERE t.driver_id = ? AND ti.parcel_id = ?
                """, targetStatus, driverId, parcelId);

        // Advance parcel status machine & write parcel_event audit log
        parcelDomainService.transitStatus(parcelId, targetStatus, String.valueOf(driverId), note);
    }
}
