package com.hf.easydelivery.common.domain;

import com.hf.easydelivery.common.exception.BizException;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Common Domain Service for Parcel lifecycle & state machine transitions.
 * Promoted to easydelivery-common so both Operations and Driver domains can access it.
 */
@Service
@Profile("!memory")
public class ParcelDomainService {

    private static final Set<String> VALID_STATUSES = Set.of(
            "CREATED", "RECEIVED", "AT_STATION", "SORTED", "READY_FOR_DISPATCH", "OUT_FOR_DELIVERY",
            "DELIVERED", "FAILED", "RETURNED_TO_UPSTREAM", "CANCELLED", "LOST"
    );

    private final JdbcTemplate jdbc;

    public ParcelDomainService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Transition a parcel to a target status with audit logging.
     */
    @Transactional
    public void transitStatus(long parcelId, String targetStatus, String operatorUserId, String reason) {
        String normalizedStatus = targetStatus.toUpperCase().trim();
        if (!VALID_STATUSES.contains(normalizedStatus)) {
            throw new BizException("PARCEL.STATUS.INVALID", "Invalid target parcel status: " + targetStatus);
        }

        // 1. Fetch current status
        List<String> currentList = jdbc.query("SELECT status FROM parcel WHERE id=?", (rs, n) -> rs.getString(1), parcelId);
        if (currentList.isEmpty()) {
            throw new BizException("PARCEL.NOT_FOUND", "Parcel not found: " + parcelId);
        }
        String currentStatus = currentList.get(0);

        if (currentStatus.equals(normalizedStatus)) {
            return; // No-op if status is already target
        }

        // 2. Update parcel status
        jdbc.update("UPDATE parcel SET status=?, updated_at=CURRENT_TIMESTAMP(3) WHERE id=?", normalizedStatus, parcelId);

        // 3. Record parcel event log for audit & traceability
        jdbc.update("""
                INSERT INTO parcel_event(parcel_id, event_type, from_status, to_status, operator_user_id, note, occurred_at)
                VALUES (?, 'STATUS_CHANGE', ?, ?, ?, ?, CURRENT_TIMESTAMP(3))
                """, parcelId, currentStatus, normalizedStatus, operatorUserId, reason);
    }
}
