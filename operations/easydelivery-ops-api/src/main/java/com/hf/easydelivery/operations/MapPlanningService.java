package com.hf.easydelivery.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hf.easydelivery.common.exception.BizException;
import com.hf.easydelivery.config.OperationsAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Profile("!memory")
public class MapPlanningService {
    private static final List<String> PLANNABLE = List.of("RECEIVED", "AT_STATION", "SORTED", "READY_FOR_DISPATCH");
    private final JdbcTemplate jdbc;
    private final OperationsAccess access;
    private final ObjectMapper mapper;

    public MapPlanningService(JdbcTemplate jdbc, OperationsAccess access, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.access = access;
        this.mapper = mapper;
    }

    public List<Map<String, Object>> shifts(LocalDate serviceDate) {
        long stationId = station();
        return jdbc.queryForList("""
                SELECT d.id driver_id,d.credential_id driver_code,d.driver_name,
                       s.id shift_id,COALESCE(s.availability_status,'AVAILABLE') availability_status,
                       COALESCE(s.parcel_capacity, COALESCE(st.default_capacity, 200)) parcel_capacity,
                       COUNT(DISTINCT CASE WHEN t.status IN ('DRAFT','FROZEN','PUBLISHED','ACCEPTING','IN_PROGRESS') THEN ti.id END) assigned_count
                FROM driver d
                JOIN station st ON st.id = d.home_station_id
                LEFT JOIN driver_shift s ON s.driver_id=d.id AND s.service_date=?
                LEFT JOIN driver_task t ON t.driver_id=d.id AND t.service_date=?
                LEFT JOIN driver_task_item ti ON ti.task_id=t.id AND ti.item_status IN ('ASSIGNED','LOADED','OUT_FOR_DELIVERY')
                WHERE d.home_station_id=? AND d.status='ACTIVE'
                GROUP BY d.id,d.credential_id,d.driver_name,s.id,s.availability_status,s.parcel_capacity,st.default_capacity
                ORDER BY d.driver_name,d.id
                """, serviceDate, serviceDate, stationId);
    }

    @Transactional
    public Map<String, Object> saveShift(ShiftRequest body) {
        long stationId = station();
        required(body.serviceDate(), "serviceDate");
        if (body.parcelCapacity() < 1 || body.parcelCapacity() > 1000) invalid("parcelCapacity must be between 1 and 1000");
        String availability = required(body.availabilityStatus(), "availabilityStatus");
        if (!List.of("AVAILABLE", "UNAVAILABLE").contains(availability)) invalid("availabilityStatus must be AVAILABLE or UNAVAILABLE");
        ensureDriver(body.driverId(), stationId);
        jdbc.update("""
                INSERT INTO driver_shift(station_id,driver_id,service_date,availability_status,parcel_capacity,note)
                VALUES (?,?,?,?,?,?) AS incoming
                ON DUPLICATE KEY UPDATE station_id=incoming.station_id,availability_status=incoming.availability_status,
                    parcel_capacity=incoming.parcel_capacity,note=incoming.note,version=driver_shift.version+1
                """, stationId, body.driverId(), body.serviceDate(), availability, body.parcelCapacity(), body.note());
        return jdbc.queryForMap("SELECT id,station_id,driver_id,service_date,availability_status,parcel_capacity,note,version FROM driver_shift WHERE driver_id=? AND service_date=?", body.driverId(), body.serviceDate());
    }

    public List<Map<String, Object>> mapParcels(LocalDate serviceDate, Double west, Double south, Double east, Double north, int limit, Long waveId) {
        return mapParcels(serviceDate, west, south, east, north, limit, waveId, null);
    }

    public List<Map<String, Object>> mapParcels(LocalDate serviceDate, Double west, Double south, Double east, Double north, int limit, Long waveId, String slaFilter) {
        long stationId = station();
        int safeLimit = Math.min(Math.max(limit, 1), 50000);
        boolean viewport = west != null && south != null && east != null && north != null;
        String viewportSql = viewport ? " AND ST_X(g.delivery_point) BETWEEN ? AND ? AND ST_Y(g.delivery_point) BETWEEN ? AND ?" : "";
        String waveSql = waveId != null ? " AND t.wave_id = ?" : "";
        // When waveId is explicitly selected, join task on waveId. When waveId is null (all waves), join task on active non-cancelled tasks.
        String taskJoinSql = waveId != null 
                ? "LEFT JOIN driver_task t ON t.id=ti.task_id AND t.wave_id = ?"
                : "LEFT JOIN driver_task t ON t.id=ti.task_id AND t.status <> 'CANCELLED'";

        String slaSql = "";
        if ("TODAY_DUE".equalsIgnoreCase(slaFilter) || "EXPRESS_ONLY".equalsIgnoreCase(slaFilter)) {
            slaSql = " AND (p.promised_date <= ? OR w.service_code IN ('EXPRESS', 'SAME_DAY', 'URGENT'))";
        } else if ("STANDARD".equalsIgnoreCase(slaFilter) || "STANDARD_ONLY".equalsIgnoreCase(slaFilter)) {
            slaSql = " AND (p.promised_date > ? OR p.promised_date IS NULL) AND (w.service_code IS NULL OR w.service_code NOT IN ('EXPRESS', 'SAME_DAY', 'URGENT'))";
        }

        String sql = """
                SELECT p.id parcel_id,p.tracking_no,p.status,p.current_custody_type,p.promised_date,w.service_code,
                       w.external_waybill_no,w.recipient_name,w.address_line1,w.city,w.postal_code,
                       ST_Longitude(g.delivery_point) longitude,ST_Latitude(g.delivery_point) latitude,
                       a.area_code,a.id area_id,a.id area_version_id,t.id task_id,t.driver_id,d.driver_name,
                       ti.stop_sequence,
                       CASE WHEN g.waybill_id IS NULL THEN 'MISSING_GEOCODE'
                             WHEN COALESCE(p.current_area_id, paa.delivery_area_id) IS NULL THEN 'UNMATCHED_AREA'
                             WHEN oc.id IS NOT NULL THEN 'OPEN_CASE' ELSE NULL END exception_code
                FROM parcel p JOIN waybill w ON w.id=p.waybill_id
                LEFT JOIN waybill_geocode g ON g.waybill_id=w.id
                LEFT JOIN parcel_area_assignment paa ON paa.parcel_id=p.id AND paa.ended_at IS NULL
                LEFT JOIN delivery_area a ON a.id=COALESCE(p.current_area_id, paa.delivery_area_id)
                LEFT JOIN driver_task_item ti ON ti.parcel_id=p.id AND ti.item_status IN ('ASSIGNED','LOADED','OUT_FOR_DELIVERY')
                """ + taskJoinSql + """
                LEFT JOIN driver d ON d.id=t.driver_id
                LEFT JOIN operational_case oc ON oc.id=(SELECT MIN(c.id) FROM operational_case c WHERE c.parcel_id=p.id AND c.status NOT IN ('RESOLVED','CLOSED'))
                WHERE p.current_station_id=? AND w.resolved_station_id=? AND w.routing_status IN ('ROUTED','OVERRIDDEN')
                  AND (p.status IN ('RECEIVED','AT_STATION','SORTED','READY_FOR_DISPATCH') OR (p.status IN ('ASSIGNED','LOADED','OUT_FOR_DELIVERY') AND t.id IS NOT NULL))
                """ + waveSql + slaSql + viewportSql + " ORDER BY p.id LIMIT ?";
        
        java.util.List<Object> params = new java.util.ArrayList<>();
        if (waveId != null) {
            params.add(waveId);
        }
        params.add(stationId);
        params.add(stationId);
        if (waveId != null) {
            params.add(waveId);
        }

        if ("TODAY_DUE".equalsIgnoreCase(slaFilter) || "EXPRESS_ONLY".equalsIgnoreCase(slaFilter) || "STANDARD".equalsIgnoreCase(slaFilter) || "STANDARD_ONLY".equalsIgnoreCase(slaFilter)) {
            params.add(serviceDate);
        }
        if (viewport) {
            params.add(west);
            params.add(east);
            params.add(south);
            params.add(north);
        }
        params.add(safeLimit);
        return jdbc.queryForList(sql, params.toArray());
    }

    public List<Map<String, Object>> unplannedParcels(LocalDate serviceDate) {
        long stationId = station();
        return jdbc.queryForList("""
                SELECT a.id AS area_id, a.area_code, a.area_name, a.id AS area_version_id, COUNT(p.id) AS unplanned_count
                FROM parcel p
                JOIN waybill w ON w.id = p.waybill_id
                LEFT JOIN parcel_area_assignment paa ON paa.parcel_id = p.id AND paa.ended_at IS NULL
                LEFT JOIN delivery_area a ON a.id = paa.delivery_area_id
                WHERE p.current_station_id = ? AND w.resolved_station_id = ?
                  AND p.status IN ('RECEIVED','AT_STATION','SORTED','READY_FOR_DISPATCH')
                  AND w.routing_status IN ('ROUTED','OVERRIDDEN')
                  AND NOT EXISTS (SELECT 1 FROM operational_case c WHERE c.parcel_id = p.id AND c.status NOT IN ('RESOLVED','CLOSED'))
                  AND NOT EXISTS (
                      SELECT 1 FROM driver_task_item ti JOIN driver_task t ON t.id = ti.task_id
                      WHERE ti.parcel_id = p.id AND t.service_date = ? AND ti.item_status IN ('ASSIGNED','LOADED','OUT_FOR_DELIVERY')
                  )
                GROUP BY a.id, a.area_code, a.area_name
                ORDER BY a.area_code
                """, stationId, stationId, serviceDate);
    }

    @Transactional
    public Map<String, Object> createWave(WaveRequest body) {
        long stationId = station();
        required(body.serviceDate(), "serviceDate");
        String code = body.waveCode();
        if (code == null || code.isBlank()) {
            String prefix;
            if (body.arrivalBatchNo() != null && !body.arrivalBatchNo().isBlank()) {
                prefix = body.arrivalBatchNo();
            } else {
                List<String> trips = jdbc.query("SELECT trip_no FROM arrival_trip WHERE station_id=? AND service_date=? ORDER BY id DESC LIMIT 1", (rs, n) -> rs.getString(1), stationId, body.serviceDate());
                if (!trips.isEmpty()) {
                    prefix = trips.get(0);
                } else {
                    prefix = "W" + body.serviceDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
                }
            }
            int seq = 1;
            while (true) {
                String candidate = prefix + "-W" + seq;
                Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_wave WHERE station_id=? AND wave_code=?", Integer.class, stationId, candidate);
                if (count == null || count == 0) {
                    code = candidate;
                    break;
                }
                seq++;
            }
        }
        String finalCode = code;
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update(c -> {
                var ps = c.prepareStatement("INSERT INTO dispatch_wave(station_id,wave_code,service_date,route_code,status) VALUES (?,?,?,?,'DRAFT')", Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, stationId); ps.setString(2, finalCode); ps.setObject(3, body.serviceDate()); ps.setString(4, body.routeCode()); return ps;
            }, keys);
        } catch (DataIntegrityViolationException ex) {
            throw new BizException("WAVE.CODE.EXISTS", "Wave code already exists at selected station");
        }

        // Auto-create corresponding Inbound Arrival Trip and its 10 default Pallets in the background
        try {
            jdbc.update("INSERT IGNORE INTO arrival_trip(station_id, external_trip_no, status, expected_at, note) VALUES (?, ?, 'EXPECTED', ?, 'Auto-created by dispatch wave planning')",
                    stationId, finalCode, java.sql.Timestamp.valueOf(body.serviceDate().atTime(8, 0)));
            Long tripId = jdbc.queryForObject("SELECT id FROM arrival_trip WHERE station_id=? AND external_trip_no=?", Long.class, stationId, finalCode);
            if (tripId != null) {
                for (int i = 1; i <= 10; i++) {
                    String unitNo = finalCode + "-U" + String.format("%02d", i);
                    jdbc.update("INSERT IGNORE INTO handling_unit(trip_id, station_id, external_unit_no, unit_type, status) VALUES (?, ?, ?, 'PALLET', 'EXPECTED')",
                            tripId, stationId, unitNo);
                }
            }
        } catch (Exception ignored) {}

        return waveSummary(keys.getKey().longValue());
    }

    public Map<String, Object> waveSummary(long waveId) {
        Wave wave = wave(waveId, false);
        return Map.of("wave", jdbc.queryForMap("SELECT id,wave_code,DATE_FORMAT(service_date, '%Y-%m-%d') AS service_date,route_code,status,frozen_at,published_at,version FROM dispatch_wave WHERE id=?", waveId),

                "drivers", jdbc.queryForList("""
                        SELECT t.id task_id,t.task_code,t.driver_id,d.driver_name,t.status,COUNT(ti.id) parcel_count,
                               COALESCE(s.parcel_capacity, COALESCE(st.default_capacity, 200)) parcel_capacity,
                               COALESCE(s.parcel_capacity, COALESCE(st.default_capacity, 200))-COUNT(ti.id) remaining_capacity
                        FROM driver_task t JOIN driver d ON d.id=t.driver_id
                        JOIN station st ON st.id=d.home_station_id
                        LEFT JOIN driver_task_item ti ON ti.task_id=t.id AND ti.item_status='ASSIGNED'
                        LEFT JOIN driver_shift s ON s.driver_id=t.driver_id AND s.service_date=t.service_date
                        WHERE t.wave_id=? GROUP BY t.id,t.task_code,t.driver_id,d.driver_name,t.status,s.parcel_capacity,st.default_capacity ORDER BY d.driver_name
                        """, wave.id()),
                "areas", jdbc.queryForList("SELECT ta.task_id,ta.delivery_area_id AS delivery_area_version_id,a.area_code,ta.assignment_mode FROM driver_task_area ta JOIN delivery_area a ON a.id=ta.delivery_area_id JOIN driver_task t ON t.id=ta.task_id WHERE t.wave_id=?", waveId));
    }

    @Transactional
    public AssignmentResult assign(long waveId, AssignmentRequest body, HttpServletRequest http) {
        Wave wave = wave(waveId, true);
        draft(wave);
        Shift shift = shift(body.driverId(), wave.serviceDate(), wave.stationId(), true);
        long taskId = task(wave, body.driverId());
        LinkedHashSet<Long> parcelIds = new LinkedHashSet<>();
        if (body.parcelIds() != null) parcelIds.addAll(body.parcelIds());
        if (body.areaVersionIds() != null) for (Long areaId : body.areaVersionIds()) {
            ensureArea(areaId, wave.stationId());
            parcelIds.addAll(jdbc.query("""
                    SELECT p.id FROM parcel_area_assignment paa JOIN parcel p ON p.id=paa.parcel_id
                    JOIN waybill w ON w.id=p.waybill_id
                    WHERE paa.delivery_area_id=? AND paa.ended_at IS NULL AND p.current_station_id=?
                      AND p.status IN ('RECEIVED','AT_STATION','SORTED','READY_FOR_DISPATCH')
                      AND NOT EXISTS (SELECT 1 FROM operational_case c WHERE c.parcel_id=p.id AND c.status NOT IN ('RESOLVED','CLOSED'))
                      AND NOT EXISTS (
                          SELECT 1 FROM driver_task_item ti JOIN driver_task t ON t.id = ti.task_id
                          WHERE ti.parcel_id = p.id AND t.service_date = ? AND ti.item_status IN ('ASSIGNED','LOADED','OUT_FOR_DELIVERY')
                      )
                    """, (rs, n) -> rs.getLong(1), areaId, wave.stationId(), wave.serviceDate()));
            jdbc.update("INSERT IGNORE INTO driver_task_area(task_id,delivery_area_id,assignment_mode,assigned_by) VALUES (?,?,?,?)", taskId, areaId, body.parcelIds() == null || body.parcelIds().isEmpty() ? "WHOLE_AREA" : "PARTIAL_AREA", operator(http));
        }
        if (parcelIds.isEmpty()) throw new BizException("ASSIGNMENT.EMPTY", "No eligible parcels were selected");
        int assigned = count(taskId);
        MapPlanningPolicy.capacity(assignedForDriver(body.driverId(), wave.serviceDate()), parcelIds.size(), shift.capacity());
        int sequence = assigned + 1;
        try {
            for (Long parcelId : parcelIds) {
                List<Long> locked = jdbc.query("""
                        SELECT p.id FROM parcel p JOIN waybill w ON w.id=p.waybill_id
                        WHERE p.id=? AND p.current_station_id=? AND p.status IN ('RECEIVED','AT_STATION','SORTED','READY_FOR_DISPATCH')
                          AND NOT EXISTS (SELECT 1 FROM operational_case c WHERE c.parcel_id=p.id AND c.status NOT IN ('RESOLVED','CLOSED')) FOR UPDATE
                        """, (rs, n) -> rs.getLong(1), parcelId, wave.stationId());
                if (locked.isEmpty()) throw new BizException("PARCEL.NOT.PLANNABLE", "Parcel is not plannable at selected station: " + parcelId);
                jdbc.update("INSERT IGNORE INTO driver_task_item(task_id,parcel_id,stop_sequence,item_status) VALUES (?,?,?,'ASSIGNED')", taskId, parcelId, sequence++);
            }
        } catch (DataIntegrityViolationException ex) {
            throw new BizException("PARCEL.ACTIVE.TASK.EXISTS", "A selected parcel already belongs to an active task");
        }
        audit(http, wave.stationId(), "PLANNING_ASSIGN", waveId, body.reason(), Map.of("taskId", taskId, "parcelCount", parcelIds.size()));
        return new AssignmentResult(waveId, taskId, parcelIds.size(), count(taskId), shift.capacity());
    }

    @Transactional
    public AssignmentResult assignDefaults(long waveId, HttpServletRequest http) {
        Wave wave = wave(waveId, true);
        draft(wave);

        List<Map<String, Object>> prefRows = jdbc.queryForList("""
                SELECT p.driver_id, a.id AS delivery_area_id
                FROM driver_area_preference p
                JOIN delivery_area a ON a.id = p.delivery_area_id
                JOIN driver d ON d.id = p.driver_id
                LEFT JOIN driver_shift s ON s.driver_id = d.id AND s.service_date = ?
                WHERE a.station_id = ? AND d.home_station_id = ? AND d.status = 'ACTIVE' AND p.status = 'ACTIVE'
                  AND COALESCE(s.availability_status, 'AVAILABLE') = 'AVAILABLE'
                  AND (p.effective_from IS NULL OR p.effective_from <= ?)
                  AND (p.effective_to IS NULL OR p.effective_to >= ?)
                ORDER BY p.priority ASC, p.id ASC
                """, wave.serviceDate(), wave.stationId(), wave.stationId(), wave.serviceDate(), wave.serviceDate());

        Map<Long, List<Long>> driverAreas = new java.util.LinkedHashMap<>();
        for (Map<String, Object> row : prefRows) {
            long dId = ((Number) row.get("driver_id")).longValue();
            long aId = ((Number) row.get("delivery_area_id")).longValue();
            driverAreas.computeIfAbsent(dId, k -> new java.util.ArrayList<>()).add(aId);
        }

        // Pre-fetch 1: Active operational cases (blocked parcel IDs) into Java Set (lightweight query)
        Set<Long> blockedCaseParcelIds = new java.util.HashSet<>(jdbc.query(
            "SELECT DISTINCT parcel_id FROM operational_case WHERE station_id = ? AND status NOT IN ('RESOLVED','CLOSED') AND parcel_id IS NOT NULL",
            (rs, n) -> rs.getLong(1), wave.stationId()));

        // Pre-fetch 2: Active assigned parcel IDs for current service date into Java Set (lightweight query)
        Set<Long> activeAssignedParcelIds = new java.util.HashSet<>(jdbc.query("""
            SELECT DISTINCT ti.parcel_id FROM driver_task_item ti
            JOIN driver_task t ON t.id = ti.task_id
            WHERE t.station_id = ? AND t.service_date = ? AND ti.item_status IN ('ASSIGNED','LOADED','OUT_FOR_DELIVERY')
            """, (rs, n) -> rs.getLong(1), wave.stationId(), wave.serviceDate()));

        int totalAssignedInCall = 0;
        for (Map.Entry<Long, List<Long>> entry : driverAreas.entrySet()) {
            long driverId = entry.getKey();
            List<Long> areaIds = entry.getValue();

            Shift s = shift(driverId, wave.serviceDate(), wave.stationId(), true);
            int currentAssigned = assignedForDriver(driverId, wave.serviceDate());
            int remainingCapacity = s.capacity() - currentAssigned;
            if (remainingCapacity <= 0) continue;

            String inSql = String.join(",", areaIds.stream().map(String::valueOf).toArray(String[]::new));
            // Ultra-simple single table query without ANY subqueries
            String querySql = """
                    SELECT DISTINCT p.id FROM parcel p
                    LEFT JOIN parcel_area_assignment paa ON paa.parcel_id = p.id AND paa.ended_at IS NULL
                    WHERE (p.current_area_id IN (%s) OR paa.delivery_area_id IN (%s))
                      AND p.current_station_id = ? AND p.status IN ('RECEIVED','AT_STATION','SORTED','READY_FOR_DISPATCH')
                    """.formatted(inSql, inSql);
            List<Long> rawParcels = jdbc.query(querySql, (rs, n) -> rs.getLong(1), wave.stationId());

            // High-performance Java-side Memory Filter
            List<Long> plannableParcels = rawParcels.stream()
                .filter(pId -> !blockedCaseParcelIds.contains(pId))
                .filter(pId -> !activeAssignedParcelIds.contains(pId))
                .limit(remainingCapacity)
                .toList();

            if (!plannableParcels.isEmpty()) {
                long taskId = task(wave, driverId);
                int seq = count(taskId) + 1;
                
                // High-performance JDBC Batch Insert for parcels
                List<Object[]> batchTaskItems = new java.util.ArrayList<>();
                for (Long pId : plannableParcels) {
                    batchTaskItems.add(new Object[]{taskId, pId, seq++, "ASSIGNED"});
                    activeAssignedParcelIds.add(pId);
                }
                jdbc.batchUpdate("INSERT IGNORE INTO driver_task_item(task_id,parcel_id,stop_sequence,item_status) VALUES (?,?,?,?)", batchTaskItems);

                // High-performance JDBC Batch Insert for area associations
                List<Object[]> batchAreas = new java.util.ArrayList<>();
                Long opUserId = operator(http);
                String mode = "WHOLE_AREA";
                for (Long areaId : areaIds) {
                    batchAreas.add(new Object[]{taskId, areaId, mode, opUserId});
                }
                jdbc.batchUpdate("INSERT IGNORE INTO driver_task_area(task_id,delivery_area_id,assignment_mode,assigned_by) VALUES (?,?,?,?)", batchAreas);

                totalAssignedInCall += plannableParcels.size();
            }
        }

        audit(http, wave.stationId(), "PLANNING_ASSIGN_DEFAULTS", waveId, "Auto assigned by driver area preferences", Map.of("assignedCount", totalAssignedInCall));
        return new AssignmentResult(waveId, 0L, totalAssignedInCall, totalAssignedInCall, 0);
    }

    @Transactional
    public AssignmentResult reassign(long waveId, long parcelId, ReassignRequest body, HttpServletRequest http) {
        Wave wave = wave(waveId, true); draft(wave);
        String reason = required(body.reason(), "reason");
        List<Long> source = jdbc.query("SELECT ti.task_id FROM driver_task_item ti JOIN driver_task t ON t.id=ti.task_id WHERE t.wave_id=? AND ti.parcel_id=? AND ti.item_status='ASSIGNED' FOR UPDATE", (rs,n)->rs.getLong(1), waveId, parcelId);
        if (source.isEmpty()) throw new BizException("ASSIGNMENT.NOT.FOUND", "Parcel is not assigned in this wave");
        Shift shift = shift(body.driverId(), wave.serviceDate(), wave.stationId(), true);
        long target = task(wave, body.driverId());
        if (source.get(0) == target) throw new BizException("ASSIGNMENT.SAME.DRIVER", "Parcel is already assigned to this driver");
        MapPlanningPolicy.capacity(assignedForDriver(body.driverId(), wave.serviceDate()), 1, shift.capacity());
        jdbc.update("UPDATE driver_task_item SET item_status='REASSIGNED' WHERE task_id=? AND parcel_id=?", source.get(0), parcelId);
        jdbc.update("INSERT IGNORE INTO driver_task_item(task_id,parcel_id,stop_sequence,item_status) VALUES (?,?,?,'ASSIGNED')", target, parcelId, count(target) + 1);
        audit(http, wave.stationId(), "PLANNING_REASSIGN", waveId, reason, Map.of("parcelId", parcelId, "fromTaskId", source.get(0), "toTaskId", target));
        return new AssignmentResult(waveId, target, 1, count(target), shift.capacity());
    }

    @Transactional
    public Map<String, Object> optimizeDriverRoute(long waveId, long driverId, HttpServletRequest http) {
        Wave wave = wave(waveId, true);
        List<Map<String, Object>> items = jdbc.queryForList("""
            SELECT ti.id AS item_id, p.id AS parcel_id,
                   ST_Latitude(g.delivery_point) AS latitude, ST_Longitude(g.delivery_point) AS longitude
            FROM driver_task_item ti
            JOIN driver_task t ON t.id = ti.task_id
            JOIN parcel p ON p.id = ti.parcel_id
            JOIN waybill w ON w.id = p.waybill_id
            LEFT JOIN waybill_geocode g ON g.waybill_id = w.id
            WHERE t.wave_id = ? AND t.driver_id = ? AND ti.item_status = 'ASSIGNED'
            """, waveId, driverId);

        if (items.isEmpty()) {
            return Map.of("optimizedCount", 0, "message", "No assigned parcels for this driver");
        }

        List<Long> itemIds = new java.util.ArrayList<>();
        List<double[]> coords = new java.util.ArrayList<>();

        for (Map<String, Object> row : items) {
            if (row.get("latitude") != null && row.get("longitude") != null) {
                itemIds.add(((Number) row.get("item_id")).longValue());
                coords.add(new double[]{((Number) row.get("longitude")).doubleValue(), ((Number) row.get("latitude")).doubleValue()});
            }
        }

        if (itemIds.isEmpty()) {
            return Map.of("optimizedCount", 0, "message", "No valid coordinates found for assigned parcels");
        }

        List<Integer> orderedIndices = new java.util.ArrayList<>();
        boolean osrmSuccess = false;

        // Option B: Pure parcel-based TSP OSRM Route Optimization (No Station Dependency)
        if (coords.size() > 1) {
            try {
                String coordString = coords.stream()
                        .map(c -> String.format(java.util.Locale.US, "%.6f,%.6f", c[0], c[1]))
                        .collect(java.util.stream.Collectors.joining(";"));

                // source=first sets the driver's first parcel as start of route across 1-N assigned areas
                String osrmUrl = "http://localhost:5001/trip/v1/driving/" + coordString + "?source=first&overview=false";
                java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                        .connectTimeout(java.time.Duration.ofMillis(1000))
                        .build();
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(osrmUrl))
                        .timeout(java.time.Duration.ofMillis(2000))
                        .GET()
                        .build();

                java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(resp.body());
                    com.fasterxml.jackson.databind.JsonNode waypoints = root.get("waypoints");
                    if (waypoints != null && waypoints.isArray()) {
                        List<int[]> waypointOrder = new java.util.ArrayList<>();
                        for (com.fasterxml.jackson.databind.JsonNode wp : waypoints) {
                            int waypointIndex = wp.get("waypoint_index").asInt();
                            int tripsIndex = wp.get("trips_index").asInt();
                            waypointOrder.add(new int[]{waypointIndex, tripsIndex});
                        }
                        waypointOrder.sort(java.util.Comparator.comparingInt(a -> a[0]));
                        for (int[] pair : waypointOrder) {
                            if (pair[1] < itemIds.size()) {
                                orderedIndices.add(pair[1]);
                            }
                        }
                        osrmSuccess = true;
                    }
                }
            } catch (Exception e) {
                // Fallback gracefully if OSRM service is offline or unreachable
                osrmSuccess = false;
            }
        }

        // Single point case
        if (coords.size() == 1) {
            orderedIndices.add(0);
            osrmSuccess = true;
        }

        // Option B Fallback: Geometric Nearest Neighbor algorithm if OSRM is offline
        if (!osrmSuccess) {
            List<Integer> remaining = new java.util.ArrayList<>();
            for (int i = 0; i < itemIds.size(); i++) remaining.add(i);
            int startIdx = remaining.remove(0);
            orderedIndices.add(startIdx);
            double curLng = coords.get(startIdx)[0], curLat = coords.get(startIdx)[1];

            while (!remaining.isEmpty()) {
                int bestIdx = 0;
                double minDist = Double.MAX_VALUE;
                for (int i = 0; i < remaining.size(); i++) {
                    int cand = remaining.get(i);
                    double[] c = coords.get(cand);
                    double dist = Math.hypot(c[0] - curLng, c[1] - curLat);
                    if (dist < minDist) {
                        minDist = dist;
                        bestIdx = i;
                    }
                }
                int picked = remaining.remove(bestIdx);
                orderedIndices.add(picked);
                curLng = coords.get(picked)[0];
                curLat = coords.get(picked)[1];
            }
        }

        // Batch update stop_sequence in MySQL (Single transaction)
        List<Object[]> batchArgs = new java.util.ArrayList<>();
        for (int seq = 0; seq < orderedIndices.size(); seq++) {
            int itemIdx = orderedIndices.get(seq);
            long itemId = itemIds.get(itemIdx);
            batchArgs.add(new Object[]{seq + 1, itemId});
        }

        jdbc.batchUpdate("UPDATE driver_task_item SET stop_sequence = ? WHERE id = ?", batchArgs);
        audit(http, wave.stationId(), "PLANNING_OSRM_OPTIMIZE", waveId, "Driver route optimized", Map.of("driverId", driverId, "parcelCount", itemIds.size(), "osrmUsed", osrmSuccess));

        return Map.of("optimizedCount", itemIds.size(), "osrmUsed", osrmSuccess);
    }

    @Transactional
    public Map<String, Object> freeze(long waveId, ReasonRequest body, HttpServletRequest http) {
        Wave wave = wave(waveId, true); draft(wave);
        String reason = required(body.reason(), "reason");
        jdbc.update("UPDATE dispatch_wave SET status='FROZEN',frozen_at=CURRENT_TIMESTAMP(3),frozen_by=?,version=version+1 WHERE id=?", operator(http), waveId);
        jdbc.update("UPDATE driver_task SET status='FROZEN',version=version+1 WHERE wave_id=?", waveId);
        audit(http, wave.stationId(), "PLANNING_FREEZE", waveId, reason, Map.of("taskCount", jdbc.queryForObject("SELECT COUNT(*) FROM driver_task WHERE wave_id=?", Integer.class, waveId)));
        return waveSummary(waveId);
    }

    @Transactional
    public Map<String, Object> publish(long waveId, ReasonRequest body, HttpServletRequest http) {
        Wave wave = wave(waveId, true);
        if (!"FROZEN".equals(wave.status())) throw new BizException("WAVE.STATE.INVALID", "Only a frozen wave can be published");
        String reason = required(body.reason(), "reason");
        jdbc.update("UPDATE dispatch_wave SET status='PUBLISHED',published_at=CURRENT_TIMESTAMP(3),published_by=?,version=version+1 WHERE id=?", operator(http), waveId);
        jdbc.update("UPDATE driver_task SET status='PUBLISHED',version=version+1 WHERE wave_id=?", waveId);
        jdbc.update("UPDATE parcel p JOIN driver_task_item ti ON ti.parcel_id=p.id JOIN driver_task t ON t.id=ti.task_id SET p.status='ASSIGNED',p.version=p.version+1 WHERE t.wave_id=? AND ti.item_status='ASSIGNED'", waveId);
        audit(http, wave.stationId(), "PLANNING_PUBLISH", waveId, reason, Map.of("custodyUnchanged", true));
        return waveSummary(waveId);
    }

    private long task(Wave wave, long driverId) {
        ensureDriver(driverId, wave.stationId());
        // 1. First check if a task for this wave and driver already exists regardless of status
        List<Long> ids = jdbc.query("SELECT id FROM driver_task WHERE wave_id=? AND driver_id=?", (rs, n) -> rs.getLong(1), wave.id(), driverId);
        if (!ids.isEmpty()) return ids.get(0);

        // 2. If not found, attempt to insert or reuse existing task_code safely
        String baseTaskCode = wave.code() + "-D" + driverId;
        List<Long> existingByCode = jdbc.query("SELECT id FROM driver_task WHERE station_id=? AND task_code=?", (rs, n) -> rs.getLong(1), wave.stationId(), baseTaskCode);
        if (!existingByCode.isEmpty()) return existingByCode.get(0);

        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(c -> {
            var ps = c.prepareStatement("INSERT INTO driver_task(wave_id,driver_id,station_id,task_code,service_date,status) VALUES (?,?,?,?,?,'DRAFT')", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, wave.id());
            ps.setLong(2, driverId);
            ps.setLong(3, wave.stationId());
            ps.setString(4, baseTaskCode);
            ps.setObject(5, wave.serviceDate());
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }


    private Wave wave(long id, boolean lock) {
        List<Wave> rows = jdbc.query("SELECT id,station_id,wave_code,service_date,status FROM dispatch_wave WHERE id=?" + (lock ? " FOR UPDATE" : ""), (rs,n)->new Wave(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getObject(4,LocalDate.class),rs.getString(5)), id);
        if (rows.isEmpty()) throw new BizException("WAVE.NOT.FOUND", "Wave not found: " + id);
        access.requireStation(rows.get(0).stationId()); return rows.get(0);
    }
    private Shift shift(long driverId, LocalDate date, long stationId, boolean lock) {
        List<Shift> rows = jdbc.query("""
                SELECT COALESCE(s.parcel_capacity, COALESCE(st.default_capacity, 200)) parcel_capacity,
                       COALESCE(s.availability_status, 'AVAILABLE') availability_status
                FROM driver d
                JOIN station st ON st.id = d.home_station_id
                LEFT JOIN driver_shift s ON s.driver_id = d.id AND s.service_date = ?
                WHERE d.id = ? AND d.home_station_id = ?
                """ + (lock ? " FOR UPDATE" : ""),
                (rs, n) -> new Shift(rs.getInt(1), rs.getString(2)), date, driverId, stationId);
        if (rows.isEmpty()) {
            return new Shift(200, "AVAILABLE");
        }
        MapPlanningPolicy.available(rows.get(0).availability());
        return rows.get(0);
    }
    private void ensureDriver(long id,long stationId){Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM driver WHERE id=? AND home_station_id=? AND status='ACTIVE'",Integer.class,id,stationId);if(n==null||n==0)throw new BizException("DRIVER.NOT.AVAILABLE","Driver is not active at selected station");}
    private void ensureArea(long areaId,long stationId){Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM delivery_area WHERE id=? AND station_id=? AND status='ACTIVE'",Integer.class,areaId,stationId);if(n==null||n==0)throw new BizException("AREA.NOT.AVAILABLE","Area does not belong to selected station");}
    private int count(long taskId){Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM driver_task_item WHERE task_id=? AND item_status='ASSIGNED'",Integer.class,taskId);return n==null?0:n;}
    private int assignedForDriver(long driverId,LocalDate date){Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM driver_task t JOIN driver_task_item ti ON ti.task_id=t.id WHERE t.driver_id=? AND t.service_date=? AND t.status IN ('DRAFT','FROZEN','PUBLISHED','ACCEPTING','IN_PROGRESS') AND ti.item_status IN ('ASSIGNED','LOADED','OUT_FOR_DELIVERY')",Integer.class,driverId,date);return n==null?0:n;}
    private void draft(Wave wave){MapPlanningPolicy.editable(wave.status());}
    private long station(){Long id=access.selectedStationId();if(id==null)throw new BizException("STATION.CONTEXT.REQUIRED","Station context is required");return id;}
    private Long operator(HttpServletRequest request){return request.getAttribute("operatorUserId") instanceof Long id?id:null;}
    private <T> T required(T value,String field){if(value==null||(value instanceof String s&&s.isBlank()))invalid(field+" is required");return value;}
    private void invalid(String message){throw new BizException("PARAM.INVALID",message);}
    private void audit(HttpServletRequest request,long stationId,String action,long waveId,String reason,Object after){try{jdbc.update("INSERT INTO operation_audit_log(operator_user_id,actor_type,actor_id,station_id,action_code,resource_type,resource_id,outcome,reason_text,after_json,request_id,occurred_at) VALUES (?,'OPERATOR',?,? ,?,'DISPATCH_WAVE',?,'SUCCESS',?,CAST(? AS JSON),?,CURRENT_TIMESTAMP(3))",operator(request),operator(request),stationId,action,String.valueOf(waveId),required(reason,"reason"),mapper.writeValueAsString(after),request.getHeader("X-Request-Id"));}catch(BizException ex){throw ex;}catch(Exception ex){throw new IllegalStateException(ex);}}

    private record Wave(long id,long stationId,String code,LocalDate serviceDate,String status){}
    private record Shift(int capacity,String availability){}
    public record ShiftRequest(long driverId,LocalDate serviceDate,String availabilityStatus,int parcelCapacity,String note){}
    public record WaveRequest(String waveCode,LocalDate serviceDate,String routeCode,String arrivalBatchNo){}
    public record AssignmentRequest(long driverId,List<Long> parcelIds,List<Long> areaVersionIds,String reason){}
    public record ReassignRequest(long driverId,String reason){}
    public record ReasonRequest(String reason){}
    public record AssignmentResult(long waveId,long taskId,int changedCount,int assignedCount,int capacity){}
}
