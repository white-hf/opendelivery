package com.hf.easydelivery.operations.domain;

import com.hf.easydelivery.common.domain.ParcelDomainService;
import com.hf.easydelivery.common.exception.BizException;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Domain Service for Operational Case (Exceptions) Aggregate.
 * Enforces atomic case status transit and automatic parcel unfreezing upon resolution.
 */
@Service
@Profile("!memory")
public class OperationalCaseDomainService {

    private final JdbcTemplate jdbc;
    private final ParcelDomainService parcelDomainService;

    public OperationalCaseDomainService(JdbcTemplate jdbc, ParcelDomainService parcelDomainService) {
        this.jdbc = jdbc;
        this.parcelDomainService = parcelDomainService;
    }

    /**
     * Resolve or close an operational case. Automatically unfreezes the associated parcel
     * back to plannable state (e.g., READY_FOR_DISPATCH / AT_STATION).
     */
    @Transactional
    public void resolveCase(long caseId, String targetStatus, String resolutionNote, String operatorUserId) {
        String normalizedStatus = targetStatus.toUpperCase().trim();
        if (!List.of("RESOLVED", "CLOSED").contains(normalizedStatus)) {
            throw new BizException("CASE.STATUS.INVALID", "Target status must be RESOLVED or CLOSED");
        }

        // 1. Fetch case info
        List<MapCaseInfo> cases = jdbc.query("SELECT id, parcel_id, station_id, status FROM operational_case WHERE id=?",
                (rs, n) -> new MapCaseInfo(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4)), caseId);
        if (cases.isEmpty()) {
            throw new BizException("CASE.NOT_FOUND", "Operational case not found: " + caseId);
        }
        MapCaseInfo caseInfo = cases.get(0);

        // 2. Update case status
        jdbc.update("""
                UPDATE operational_case SET status=?, resolution_note=?, resolved_at=CURRENT_TIMESTAMP(3),
                       resolved_by=?, version=version+1 WHERE id=?
                """, normalizedStatus, resolutionNote, operatorUserId, caseId);

        // 3. Record case event log
        jdbc.update("""
                INSERT INTO operational_case_event(case_id, event_type, from_status, to_status, operator_user_id, note, occurred_at)
                VALUES (?, 'CASE_RESOLVED', ?, ?, ?, ?, CURRENT_TIMESTAMP(3))
                """, caseId, caseInfo.status(), normalizedStatus, operatorUserId, resolutionNote);

        // 4. Check if parcel has any other open cases; if not, unfreeze parcel status
        Integer openCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM operational_case WHERE parcel_id=? AND status NOT IN ('RESOLVED', 'CLOSED') AND id<>?
                """, Integer.class, caseInfo.parcelId(), caseId);

        if (openCount == null || openCount == 0) {
            parcelDomainService.transitStatus(caseInfo.parcelId(), "AT_STATION", operatorUserId, "Case #" + caseId + " resolved");
        }
    }

    public record MapCaseInfo(long id, long parcelId, long stationId, String status) {}
}

