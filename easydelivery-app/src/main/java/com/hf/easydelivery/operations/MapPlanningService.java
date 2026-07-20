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
                       s.id shift_id,COALESCE(s.availability_status,'UNAVAILABLE') availability_status,
                       s.parcel_capacity,COUNT(DISTINCT CASE WHEN t.status IN ('DRAFT','FROZEN','PUBLISHED','ACCEPTING','IN_PROGRESS') THEN ti.id END) assigned_count
                FROM driver d LEFT JOIN driver_shift s ON s.driver_id=d.id AND s.service_date=?
                LEFT JOIN driver_task t ON t.driver_id=d.id AND t.service_date=?
                LEFT JOIN driver_task_item ti ON ti.task_id=t.id AND ti.item_status IN ('ASSIGNED','LOADED','OUT_FOR_DELIVERY')
                WHERE d.home_station_id=? AND d.status='ACTIVE'
                GROUP BY d.id,d.credential_id,d.driver_name,s.id,s.availability_status,s.parcel_capacity
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

    public List<Map<String, Object>> mapParcels(LocalDate serviceDate, Double west, Double south, Double east, Double north, int limit) {
        long stationId = station();
        int safeLimit = Math.min(Math.max(limit, 1), 2000);
        boolean viewport = west != null && south != null && east != null && north != null;
        String viewportSql = viewport ? " AND ST_X(g.delivery_point) BETWEEN ? AND ? AND ST_Y(g.delivery_point) BETWEEN ? AND ?" : "";
        String sql = """
                SELECT p.id parcel_id,p.tracking_no,p.status,p.current_custody_type,p.promised_date,
                       w.external_waybill_no,w.recipient_name,w.address_line1,w.city,w.postal_code,
                       ST_X(g.delivery_point) longitude,ST_Y(g.delivery_point) latitude,
                       a.area_code,av.id area_version_id,t.id task_id,t.driver_id,d.driver_name,
                       CASE WHEN g.waybill_id IS NULL THEN 'MISSING_GEOCODE'
                            WHEN paa.id IS NULL THEN 'UNMATCHED_AREA'
                            WHEN oc.id IS NOT NULL THEN 'OPEN_CASE' ELSE NULL END exception_code
                FROM parcel p JOIN waybill w ON w.id=p.waybill_id
                LEFT JOIN waybill_geocode g ON g.waybill_id=w.id
                LEFT JOIN parcel_area_assignment paa ON paa.parcel_id=p.id AND paa.ended_at IS NULL
                LEFT JOIN delivery_area_version av ON av.id=paa.delivery_area_version_id
                LEFT JOIN delivery_area a ON a.id=av.delivery_area_id
                LEFT JOIN driver_task_item ti ON ti.parcel_id=p.id AND ti.item_status IN ('ASSIGNED','LOADED','OUT_FOR_DELIVERY')
                LEFT JOIN driver_task t ON t.id=ti.task_id AND t.service_date=?
                LEFT JOIN driver d ON d.id=t.driver_id
                LEFT JOIN operational_case oc ON oc.id=(SELECT MIN(c.id) FROM operational_case c WHERE c.parcel_id=p.id AND c.status NOT IN ('RESOLVED','CLOSED'))
                WHERE p.current_station_id=? AND w.resolved_station_id=? AND w.routing_status IN ('ROUTED','OVERRIDDEN')
                  AND (p.status IN ('RECEIVED','AT_STATION','SORTED','READY_FOR_DISPATCH') OR (p.status='ASSIGNED' AND t.id IS NOT NULL))
                """ + viewportSql + " ORDER BY p.id LIMIT ?";
        if (viewport) return jdbc.queryForList(sql, serviceDate, stationId, stationId, west, east, south, north, safeLimit);
        return jdbc.queryForList(sql, serviceDate, stationId, stationId, safeLimit);
    }

    @Transactional
    public Map<String, Object> createWave(WaveRequest body) {
        long stationId = station();
        String code = required(body.waveCode(), "waveCode");
        required(body.serviceDate(), "serviceDate");
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update(c -> {
                var ps = c.prepareStatement("INSERT INTO dispatch_wave(station_id,wave_code,service_date,route_code,status) VALUES (?,?,?,?,'DRAFT')", Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, stationId); ps.setString(2, code); ps.setObject(3, body.serviceDate()); ps.setString(4, body.routeCode()); return ps;
            }, keys);
        } catch (DataIntegrityViolationException ex) {
            throw new BizException("WAVE.CODE.EXISTS", "Wave code already exists at selected station");
        }
        return waveSummary(keys.getKey().longValue());
    }

    public Map<String, Object> waveSummary(long waveId) {
        Wave wave = wave(waveId, false);
        return Map.of("wave", jdbc.queryForMap("SELECT id,wave_code,service_date,route_code,status,frozen_at,published_at,version FROM dispatch_wave WHERE id=?", waveId),
                "drivers", jdbc.queryForList("""
                        SELECT t.id task_id,t.task_code,t.driver_id,d.driver_name,t.status,COUNT(ti.id) parcel_count,s.parcel_capacity,
                               s.parcel_capacity-COUNT(ti.id) remaining_capacity
                        FROM driver_task t JOIN driver d ON d.id=t.driver_id
                        LEFT JOIN driver_task_item ti ON ti.task_id=t.id AND ti.item_status='ASSIGNED'
                        LEFT JOIN driver_shift s ON s.driver_id=t.driver_id AND s.service_date=t.service_date
                        WHERE t.wave_id=? GROUP BY t.id,t.task_code,t.driver_id,d.driver_name,t.status,s.parcel_capacity ORDER BY d.driver_name
                        """, wave.id()),
                "areas", jdbc.queryForList("SELECT ta.task_id,ta.delivery_area_version_id,a.area_code,ta.assignment_mode FROM driver_task_area ta JOIN delivery_area_version av ON av.id=ta.delivery_area_version_id JOIN delivery_area a ON a.id=av.delivery_area_id JOIN driver_task t ON t.id=ta.task_id WHERE t.wave_id=?", waveId));
    }

    @Transactional
    public AssignmentResult assign(long waveId, AssignmentRequest body, HttpServletRequest http) {
        Wave wave = wave(waveId, true);
        draft(wave);
        Shift shift = shift(body.driverId(), wave.serviceDate(), wave.stationId(), true);
        long taskId = task(wave, body.driverId());
        LinkedHashSet<Long> parcelIds = new LinkedHashSet<>();
        if (body.parcelIds() != null) parcelIds.addAll(body.parcelIds());
        if (body.areaVersionIds() != null) for (Long areaVersionId : body.areaVersionIds()) {
            ensureArea(areaVersionId, wave.stationId());
            parcelIds.addAll(jdbc.query("""
                    SELECT p.id FROM parcel_area_assignment paa JOIN parcel p ON p.id=paa.parcel_id
                    JOIN waybill w ON w.id=p.waybill_id
                    WHERE paa.delivery_area_version_id=? AND paa.ended_at IS NULL AND p.current_station_id=?
                      AND p.status IN ('RECEIVED','AT_STATION','SORTED','READY_FOR_DISPATCH')
                      AND w.routing_status IN ('ROUTED','OVERRIDDEN') AND w.resolved_station_id=?
                      AND NOT EXISTS (SELECT 1 FROM operational_case c WHERE c.parcel_id=p.id AND c.status NOT IN ('RESOLVED','CLOSED'))
                    """, (rs, n) -> rs.getLong(1), areaVersionId, wave.stationId(), wave.stationId()));
            jdbc.update("INSERT IGNORE INTO driver_task_area(task_id,delivery_area_version_id,assignment_mode,assigned_by) VALUES (?,?,?,?)", taskId, areaVersionId, body.parcelIds() == null || body.parcelIds().isEmpty() ? "WHOLE_AREA" : "PARTIAL_AREA", operator(http));
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
                          AND w.routing_status IN ('ROUTED','OVERRIDDEN') AND w.resolved_station_id=?
                          AND NOT EXISTS (SELECT 1 FROM operational_case c WHERE c.parcel_id=p.id AND c.status NOT IN ('RESOLVED','CLOSED')) FOR UPDATE
                        """, (rs, n) -> rs.getLong(1), parcelId, wave.stationId(), wave.stationId());
                if (locked.isEmpty()) throw new BizException("PARCEL.NOT.PLANNABLE", "Parcel is not plannable at selected station: " + parcelId);
                jdbc.update("INSERT INTO driver_task_item(task_id,parcel_id,stop_sequence,item_status) VALUES (?,?,?,'ASSIGNED')", taskId, parcelId, sequence++);
            }
        } catch (DataIntegrityViolationException ex) {
            throw new BizException("PARCEL.ACTIVE.TASK.EXISTS", "A selected parcel already belongs to an active task");
        }
        audit(http, wave.stationId(), "PLANNING_ASSIGN", waveId, body.reason(), Map.of("taskId", taskId, "parcelCount", parcelIds.size()));
        return new AssignmentResult(waveId, taskId, parcelIds.size(), count(taskId), shift.capacity());
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
        jdbc.update("INSERT INTO driver_task_item(task_id,parcel_id,stop_sequence,item_status) VALUES (?,?,?,'ASSIGNED')", target, parcelId, count(target) + 1);
        audit(http, wave.stationId(), "PLANNING_REASSIGN", waveId, reason, Map.of("parcelId", parcelId, "fromTaskId", source.get(0), "toTaskId", target));
        return new AssignmentResult(waveId, target, 1, count(target), shift.capacity());
    }

    @Transactional
    public Map<String, Object> freeze(long waveId, ReasonRequest body, HttpServletRequest http) {
        Wave wave = wave(waveId, true); draft(wave);
        String reason = required(body.reason(), "reason");
        List<Map<String,Object>> invalid = jdbc.queryForList("""
                SELECT t.id task_id,t.driver_id,COUNT(ti.id) parcel_count,s.parcel_capacity,s.availability_status
                FROM driver_task t LEFT JOIN driver_task_item ti ON ti.task_id=t.id AND ti.item_status='ASSIGNED'
                LEFT JOIN driver_shift s ON s.driver_id=t.driver_id AND s.service_date=t.service_date
                WHERE t.wave_id=? GROUP BY t.id,t.driver_id,s.parcel_capacity,s.availability_status
                HAVING parcel_count=0 OR parcel_capacity IS NULL OR availability_status<>'AVAILABLE'
                  OR (SELECT COUNT(*) FROM driver_task all_t JOIN driver_task_item all_i ON all_i.task_id=all_t.id
                      WHERE all_t.driver_id=t.driver_id AND all_t.service_date=t.service_date
                        AND all_t.status IN ('DRAFT','FROZEN','PUBLISHED','ACCEPTING','IN_PROGRESS')
                        AND all_i.item_status IN ('ASSIGNED','LOADED','OUT_FOR_DELIVERY'))>parcel_capacity
                """, waveId);
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM driver_task_item ti JOIN driver_task t ON t.id=ti.task_id WHERE t.wave_id=? AND ti.item_status='ASSIGNED'", Integer.class, waveId);
        if (total == null || total == 0) throw new BizException("WAVE.EMPTY", "Wave has no assigned parcels");
        if (!invalid.isEmpty()) throw new BizException("WAVE.PREFLIGHT.FAILED", "Driver availability, empty-task, or capacity preflight failed");
        jdbc.update("UPDATE dispatch_wave SET status='FROZEN',frozen_at=CURRENT_TIMESTAMP(3),frozen_by=?,version=version+1 WHERE id=?", operator(http), waveId);
        jdbc.update("UPDATE driver_task SET status='FROZEN',version=version+1 WHERE wave_id=?", waveId);
        audit(http, wave.stationId(), "PLANNING_FREEZE", waveId, reason, Map.of("parcelCount", total));
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
        List<Long> ids = jdbc.query("SELECT id FROM driver_task WHERE wave_id=? AND driver_id=? AND status='DRAFT'", (rs,n)->rs.getLong(1), wave.id(), driverId);
        if (!ids.isEmpty()) return ids.get(0);
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO driver_task(wave_id,driver_id,station_id,task_code,service_date,status) VALUES (?,?,?,?,?,'DRAFT')",Statement.RETURN_GENERATED_KEYS);ps.setLong(1,wave.id());ps.setLong(2,driverId);ps.setLong(3,wave.stationId());ps.setString(4,wave.code()+"-D"+driverId);ps.setObject(5,wave.serviceDate());return ps;},keys);
        return keys.getKey().longValue();
    }

    private Wave wave(long id, boolean lock) {
        List<Wave> rows = jdbc.query("SELECT id,station_id,wave_code,service_date,status FROM dispatch_wave WHERE id=?" + (lock ? " FOR UPDATE" : ""), (rs,n)->new Wave(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getObject(4,LocalDate.class),rs.getString(5)), id);
        if (rows.isEmpty()) throw new BizException("WAVE.NOT.FOUND", "Wave not found: " + id);
        access.requireStation(rows.get(0).stationId()); return rows.get(0);
    }
    private Shift shift(long driverId, LocalDate date, long stationId, boolean lock) {
        List<Shift> rows=jdbc.query("SELECT parcel_capacity,availability_status FROM driver_shift WHERE driver_id=? AND station_id=? AND service_date=?"+(lock?" FOR UPDATE":""),(rs,n)->new Shift(rs.getInt(1),rs.getString(2)),driverId,stationId,date);
        if(rows.isEmpty()) throw new BizException("DRIVER.SHIFT.UNAVAILABLE","Driver has no available shift for the service date");MapPlanningPolicy.available(rows.get(0).availability());return rows.get(0);
    }
    private void ensureDriver(long id,long stationId){Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM driver WHERE id=? AND home_station_id=? AND status='ACTIVE'",Integer.class,id,stationId);if(n==null||n==0)throw new BizException("DRIVER.NOT.AVAILABLE","Driver is not active at selected station");}
    private void ensureArea(long versionId,long stationId){Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM delivery_area_version av JOIN delivery_area a ON a.id=av.delivery_area_id WHERE av.id=? AND a.station_id=? AND a.status='ACTIVE' AND av.status='PUBLISHED'",Integer.class,versionId,stationId);if(n==null||n==0)throw new BizException("AREA.NOT.AVAILABLE","Published area does not belong to selected station");}
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
    public record WaveRequest(String waveCode,LocalDate serviceDate,String routeCode){}
    public record AssignmentRequest(long driverId,List<Long> parcelIds,List<Long> areaVersionIds,String reason){}
    public record ReassignRequest(long driverId,String reason){}
    public record ReasonRequest(String reason){}
    public record AssignmentResult(long waveId,long taskId,int changedCount,int assignedCount,int capacity){}
}
