package com.hf.easydelivery.operations.reconciliation.service;

import com.hf.easydelivery.common.exception.BizException;
import com.hf.easydelivery.config.OperationsAccess;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.hf.easydelivery.operations.reconciliation.persistence.HandoverQueryRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Profile("!memory")
public class ScanSupervisionService {
    private final JdbcTemplate jdbc;
    private final OperationsAccess access;
    private final HandoverQueryRepository queryRepository;

    public ScanSupervisionService(JdbcTemplate jdbc, OperationsAccess access, HandoverQueryRepository queryRepository) {
        this.jdbc = jdbc;
        this.access = access;
        this.queryRepository = queryRepository;
    }

    private long station() {
        Long id = access.selectedStationId();
        if (id == null) {
            throw new BizException("STATION.CONTEXT.REQUIRED", "Station context is required");
        }
        return id;
    }

    public record TaskSupervisionSummary(
            long taskId,
            long driverId,
            String driverName,
            int expectedCount,
            int validCount,
            int missingCount,
            int wrongTaskCount,
            int unknownCount,
            int duplicateCount,
            int extraCount,
            int openSessionCount
    ) {}

    public record WaveSupervisionSummary(
            long waveId,
            String waveCode,
            int expectedCount,
            int validCount,
            int missingCount,
            int wrongTaskCount,
            int unknownCount,
            int duplicateCount,
            int extraCount,
            int openSessionCount,
            List<TaskSupervisionSummary> tasks
    ) {}

    public record SupervisionResponse(
            long stationId,
            LocalDate serviceDate,
            int totalExpectedCount,
            int totalValidCount,
            int totalMissingCount,
            int totalWrongTaskCount,
            int totalUnknownCount,
            int totalDuplicateCount,
            int totalExtraCount,
            int totalOpenSessionCount,
            List<WaveSupervisionSummary> waves
    ) {}

    public record SessionSummary(
            long sessionId,
            long taskId,
            long driverId,
            String driverName,
            String sessionStatus,
            LocalDateTime openedAt,
            LocalDateTime submittedAt,
            int expectedCount,
            int scannedCount,
            int discrepancyCount,
            int validCount,
            int wrongTaskCount,
            int unknownCount,
            int duplicateCount,
            int extraCount
    ) {}

    public record ScanEventDetail(
            long eventId,
            long sessionId,
            Long parcelId,
            String trackingNo,
            String resultCode,
            String deviceEventId,
            LocalDateTime scannedAt,
            Long correctTaskId,
            String correctDriverName
    ) {}

    /**
     * Escape-hatch: Aggregate scan supervision facts across wave -> task -> driver for the station.
     */
    public SupervisionResponse supervision(LocalDate serviceDate, Long waveIdFilter) {
        long stationId = station();

        List<Map<String, Object>> taskRows = queryRepository.tasks(stationId, serviceDate, waveIdFilter);

        Map<Long, Integer> expectedByTask = new HashMap<>();
        List<Map<String, Object>> expectedRows = queryRepository.expected(stationId, serviceDate);
        for (Map<String, Object> r : expectedRows) {
            expectedByTask.put(((Number) r.get("task_id")).longValue(), ((Number) r.get("cnt")).intValue());
        }

        List<Map<String, Object>> eventRows = queryRepository.eventCounts(stationId, serviceDate);

        Map<Long, Map<String, Integer>> eventCountsByTask = new HashMap<>();
        Map<Long, Integer> validCountsByTask = new HashMap<>();

        for (Map<String, Object> r : eventRows) {
            long taskId = ((Number) r.get("task_id")).longValue();
            String code = (String) r.get("result_code");
            int cnt = ((Number) r.get("cnt")).intValue();

            eventCountsByTask.computeIfAbsent(taskId, k -> new HashMap<>()).put(code, cnt);
            if ("EXPECTED".equals(code)) {
                int distinctTracking = ((Number) r.get("distinct_tracking")).intValue();
                validCountsByTask.put(taskId, distinctTracking);
            }
        }

        Map<Long, Integer> openSessionsByTask = new HashMap<>();
        List<Map<String, Object>> openSessionRows = queryRepository.openSessions(stationId, serviceDate);
        for (Map<String, Object> r : openSessionRows) {
            openSessionsByTask.put(((Number) r.get("task_id")).longValue(), ((Number) r.get("cnt")).intValue());
        }

        Map<Long, List<TaskSupervisionSummary>> waveTaskMap = new LinkedHashMap<>();
        Map<Long, String> waveCodeMap = new LinkedHashMap<>();

        int totExp = 0, totVal = 0, totMiss = 0, totWrong = 0, totUnk = 0, totDup = 0, totExtra = 0, totOpen = 0;

        for (Map<String, Object> row : taskRows) {
            long wId = ((Number) row.get("wave_id")).longValue();
            String wCode = (String) row.get("wave_code");
            long tId = ((Number) row.get("task_id")).longValue();
            long dId = ((Number) row.get("driver_id")).longValue();
            String dName = (String) row.get("driver_name");

            waveCodeMap.put(wId, wCode);

            int exp = expectedByTask.getOrDefault(tId, 0);
            int val = validCountsByTask.getOrDefault(tId, 0);
            int miss = ScanSupervisionPolicy.calculateMissing(exp, val);

            Map<String, Integer> counts = eventCountsByTask.getOrDefault(tId, Collections.emptyMap());
            int wrong = counts.getOrDefault("WRONG_TASK", 0);
            int unk = counts.getOrDefault("UNKNOWN", 0);
            int dup = counts.getOrDefault("DUPLICATE", 0);
            int extra = counts.getOrDefault("EXTRA", 0);
            int openSess = openSessionsByTask.getOrDefault(tId, 0);

            totExp += exp;
            totVal += val;
            totMiss += miss;
            totWrong += wrong;
            totUnk += unk;
            totDup += dup;
            totExtra += extra;
            totOpen += openSess;

            TaskSupervisionSummary taskSum = new TaskSupervisionSummary(
                    tId, dId, dName, exp, val, miss, wrong, unk, dup, extra, openSess
            );
            waveTaskMap.computeIfAbsent(wId, k -> new ArrayList<>()).add(taskSum);
        }

        List<WaveSupervisionSummary> waveSummaries = new ArrayList<>();
        for (Map.Entry<Long, List<TaskSupervisionSummary>> entry : waveTaskMap.entrySet()) {
            long wId = entry.getKey();
            String wCode = waveCodeMap.get(wId);
            List<TaskSupervisionSummary> tList = entry.getValue();

            int wExp = 0, wVal = 0, wMiss = 0, wWrong = 0, wUnk = 0, wDup = 0, wExtra = 0, wOpen = 0;
            for (TaskSupervisionSummary ts : tList) {
                wExp += ts.expectedCount();
                wVal += ts.validCount();
                wMiss += ts.missingCount();
                wWrong += ts.wrongTaskCount();
                wUnk += ts.unknownCount();
                wDup += ts.duplicateCount();
                wExtra += ts.extraCount();
                wOpen += ts.openSessionCount();
            }

            waveSummaries.add(new WaveSupervisionSummary(
                    wId, wCode, wExp, wVal, wMiss, wWrong, wUnk, wDup, wExtra, wOpen, tList
            ));
        }

        return new SupervisionResponse(
                stationId, serviceDate, totExp, totVal, totMiss, totWrong, totUnk, totDup, totExtra, totOpen, waveSummaries
        );
    }

    public List<SessionSummary> sessions(Long taskId, String status, LocalDate serviceDate) {
        long stationId = station();
        StringBuilder sql = new StringBuilder("""
                SELECT s.id, s.task_id, s.driver_id, d.driver_name AS driver_name, s.status,
                       s.opened_at, s.submitted_at, s.expected_count, s.scanned_count, s.discrepancy_count,
                       s.valid_count AS valid_cnt,
                       0 AS wrong_cnt,
                       s.unknown_count AS unk_cnt,
                       0 AS dup_cnt,
                       s.extra_count AS extra_cnt
                FROM scan_session s
                JOIN driver_task t ON t.id = s.task_id
                JOIN dispatch_wave w ON w.id = t.wave_id
                JOIN driver d ON d.id = s.driver_id
                WHERE w.station_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(stationId);

        if (taskId != null) {
            sql.append(" AND s.task_id = ?");
            args.add(taskId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND s.status = ?");
            args.add(status);
        }
        if (serviceDate != null) {
            sql.append(" AND w.service_date = ?");
            args.add(serviceDate);
        }

        sql.append(" ORDER BY s.id DESC");

        return jdbc.query(sql.toString(), (rs, rowNum) -> new SessionSummary(
                rs.getLong("id"),
                rs.getLong("task_id"),
                rs.getLong("driver_id"),
                rs.getString("driver_name"),
                rs.getString("status"),
                rs.getTimestamp("opened_at") != null ? rs.getTimestamp("opened_at").toLocalDateTime() : null,
                rs.getTimestamp("submitted_at") != null ? rs.getTimestamp("submitted_at").toLocalDateTime() : null,
                rs.getInt("expected_count"),
                rs.getInt("scanned_count"),
                rs.getInt("discrepancy_count"),
                rs.getInt("valid_cnt"),
                rs.getInt("wrong_cnt"),
                rs.getInt("unk_cnt"),
                rs.getInt("dup_cnt"),
                rs.getInt("extra_cnt")
        ), args.toArray());
    }

    public List<ScanEventDetail> sessionEvents(long sessionId) {
        long stationId = station();
        if (!queryRepository.belongsToStation(sessionId, stationId)) {
            return Collections.emptyList();
        }

        return jdbc.query("""
                SELECT e.id AS event_id, e.session_id, e.parcel_id, e.tracking_no, e.result_code,
                       e.device_event_id, e.scanned_at,
                       ct.id AS correct_task_id, cd.name AS correct_driver_name
                FROM scan_event e
                LEFT JOIN parcel p ON p.tracking_number = e.tracking_no
                LEFT JOIN driver_task_item ti ON ti.parcel_id = p.id AND ti.item_status != 'CANCELLED'
                LEFT JOIN driver_task ct ON ct.id = ti.task_id
                LEFT JOIN driver cd ON cd.id = ct.driver_id
                WHERE e.session_id = ?
                ORDER BY e.scanned_at ASC, e.id ASC
                """, (rs, rowNum) -> new ScanEventDetail(
                rs.getLong("event_id"),
                rs.getLong("session_id"),
                rs.getObject("parcel_id") != null ? rs.getLong("parcel_id") : null,
                rs.getString("tracking_no"),
                rs.getString("result_code"),
                rs.getString("device_event_id"),
                rs.getTimestamp("scanned_at") != null ? rs.getTimestamp("scanned_at").toLocalDateTime() : null,
                rs.getObject("correct_task_id") != null ? rs.getLong("correct_task_id") : null,
                rs.getString("correct_driver_name")
        ), sessionId);
    }
}
