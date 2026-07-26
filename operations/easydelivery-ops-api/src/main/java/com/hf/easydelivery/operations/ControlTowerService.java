package com.hf.easydelivery.operations;

import com.hf.easydelivery.config.OperationsAccess;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Profile("!memory")
public class ControlTowerService {
    private final JdbcTemplate jdbc;
    private final OperationsAccess access;

    public ControlTowerService(JdbcTemplate jdbc, OperationsAccess access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    public Snapshot snapshot(LocalDate serviceDate) {
        long stationId = station();
        Map<String,Object> station=jdbc.queryForMap("SELECT id,station_code,station_name,city,province_code,timezone,status FROM station WHERE id=?",stationId);
        int expected=count("SELECT COUNT(*) FROM parcel WHERE current_station_id=? AND promised_date=? AND status<>'CANCELLED' AND is_test=0",stationId,serviceDate);
        int routed=count("SELECT COUNT(*) FROM parcel p JOIN waybill w ON w.id=p.waybill_id WHERE p.current_station_id=? AND p.promised_date=? AND w.resolved_station_id=? AND w.routing_status IN ('ROUTED','OVERRIDDEN') AND p.is_test=0",stationId,serviceDate,stationId);
        int geocoded=count("SELECT COUNT(*) FROM parcel p JOIN waybill_geocode g ON g.waybill_id=p.waybill_id WHERE p.current_station_id=? AND p.promised_date=? AND p.is_test=0",stationId,serviceDate);
        int areaMatched=count("SELECT COUNT(*) FROM parcel p WHERE p.current_station_id=? AND p.promised_date=? AND p.current_area_id IS NOT NULL AND p.is_test=0",stationId,serviceDate);
        int assigned=count("SELECT COUNT(DISTINCT ti.parcel_id) FROM driver_task t JOIN driver_task_item ti ON ti.task_id=t.id AND ti.item_status IN ('ASSIGNED','LOADED','OUT_FOR_DELIVERY') WHERE t.station_id=? AND t.service_date=? AND t.status IN ('DRAFT','FROZEN','PUBLISHED','ACCEPTING','IN_PROGRESS') AND t.is_test=0",stationId,serviceDate);
        int arrived=count("SELECT COUNT(*) FROM parcel WHERE current_station_id=? AND promised_date=? AND current_custody_type='STATION' AND is_test=0",stationId,serviceDate);
        int scanned=count("SELECT COUNT(DISTINCT se.parcel_id) FROM scan_event se JOIN scan_session ss ON ss.id=se.session_id JOIN driver_task t ON t.id=ss.task_id WHERE t.station_id=? AND t.service_date=? AND se.result_code='EXPECTED' AND ss.is_test=0",stationId,serviceDate);
        int released=count("SELECT COUNT(DISTINCT ti.parcel_id) FROM driver_task t JOIN driver_task_item ti ON ti.task_id=t.id WHERE t.station_id=? AND t.service_date=? AND t.status IN ('ACCEPTING','IN_PROGRESS','CLOSED') AND ti.item_status IN ('LOADED','OUT_FOR_DELIVERY','DELIVERED','FAILED','RETURNED') AND t.is_test=0",stationId,serviceDate);
        int out=count("SELECT COUNT(*) FROM parcel WHERE current_station_id=? AND promised_date=? AND status='OUT_FOR_DELIVERY' AND is_test=0",stationId,serviceDate);
        int delivered=count("SELECT COUNT(*) FROM parcel WHERE current_station_id=? AND promised_date=? AND status='DELIVERED' AND is_test=0",stationId,serviceDate);
        int failed=count("SELECT COUNT(*) FROM parcel WHERE current_station_id=? AND promised_date=? AND status IN ('DELIVERY_FAILED','RETURN_PENDING','RETURNED_TO_STATION') AND is_test=0",stationId,serviceDate);
        int openCases=count("SELECT COUNT(*) FROM operational_case c WHERE c.status NOT IN ('RESOLVED','CLOSED') AND c.is_test=0 AND (c.station_id=? OR (c.station_id IS NULL AND c.parcel_id IN (SELECT id FROM parcel WHERE current_station_id=?)))",stationId,stationId);

        int openManifests=count("SELECT COUNT(*) FROM inbound_manifest WHERE station_id=? AND status NOT IN ('CLOSED','CANCELLED')",stationId);
        int availableDrivers=count("SELECT COUNT(*) FROM driver_shift WHERE station_id=? AND service_date=? AND availability_status='AVAILABLE'",stationId,serviceDate);
        int capacity=count("SELECT COALESCE(SUM(parcel_capacity),0) FROM driver_shift WHERE station_id=? AND service_date=? AND availability_status='AVAILABLE'",stationId,serviceDate);
        int missingGeocode=Math.max(0,routed-geocoded), unmatched=Math.max(0,geocoded-areaMatched), unassigned=Math.max(0,areaMatched-assigned), shortage=Math.max(0,areaMatched-capacity);

        List<Metric> metrics=List.of(metric("EXPECTED",expected,"orders",null),metric("ROUTED",routed,"orders","routing"),metric("AREA_MATCHED",areaMatched,"dispatch","unassigned"),metric("ASSIGNED",assigned,"dispatch","assigned"),metric("ARRIVED",arrived,"manifests",null),metric("SCANNED",scanned,"scanning",null),metric("RELEASED",released,"handover",null),metric("OUT_FOR_DELIVERY",out,"delivery","active"),metric("DELIVERED",delivered,"delivery","delivered"),metric("FAILED_RETURNED",failed,"delivery","exceptions"));
        List<ExceptionItem> exceptions=new ArrayList<>();
        addException(exceptions,"MISSING_GEOCODE",missingGeocode,"ERROR","orders","missing-geocode");
        addException(exceptions,"UNMATCHED_AREA",unmatched,"ERROR","dispatch","unmatched-area");
        addException(exceptions,"UNASSIGNED",unassigned,"WARNING","dispatch","unassigned");
        addException(exceptions,"CAPACITY_SHORTAGE",shortage,"ERROR","dispatch","capacity");
        addException(exceptions,"OPEN_CASE",openCases,"ERROR","cases","open");
        addException(exceptions,"OPEN_MANIFEST",openManifests,"INFO","manifests","open");

        List<Stage> stages=List.of(
                stage("ORDER_READINESS",expected,areaMatched,missingGeocode+unmatched,"orders"),
                stage("DISPATCH_PLANNING",areaMatched,assigned,unassigned+shortage,"dispatch"),
                stage("INBOUND_ARRIVAL",expected,arrived,openManifests,"manifests"),
                stage("DRIVER_SCAN",assigned,scanned,Math.max(0,assigned-scanned),"scanning"),
                stage("HANDOVER_APPROVAL",scanned,released,Math.max(0,scanned-released),"handover"),
                stage("DELIVERY",released,delivered+failed,Math.max(0,released-delivered-failed),"delivery"),
                stage("DAY_CLOSE",Math.max(released,1),delivered+failed,openCases,"closeout"));
        List<ActionItem> actions=new ArrayList<>();
        if(missingGeocode>0)actions.add(action("RESOLVE_MISSING_GEOCODE",missingGeocode,"ERROR","orders","missing-geocode"));
        if(unmatched>0)actions.add(action("RESOLVE_UNMATCHED_AREA",unmatched,"ERROR","dispatch","unmatched-area"));
        if(shortage>0)actions.add(action("RESOLVE_CAPACITY_SHORTAGE",shortage,"ERROR","dispatch","capacity"));
        if(unassigned>0)actions.add(action("ASSIGN_PARCELS",unassigned,"WARNING","dispatch","unassigned"));
        if(openManifests>0)actions.add(action("CONTINUE_INBOUND",openManifests,"INFO","manifests","open"));
        if(openCases>0)actions.add(action("RESOLVE_CASES",openCases,"ERROR","cases","open"));
        if(actions.isEmpty())actions.add(action("OPERATIONS_ON_TRACK",0,"SUCCESS","dashboard",null));
        return new Snapshot(station,serviceDate,OffsetDateTime.now(),metrics,stages,
                new Capacity(availableDrivers,capacity,assigned,Math.max(0,capacity-assigned),shortage),exceptions,actions);
    }

    public List<DriverSupervisionItem> onRoadSupervision(LocalDate serviceDate) {
        long stationId = station();
        String sql = """
            SELECT d.id AS driver_id, d.driver_name AS driver_name,
                   COALESCE(da.area_code, 'DEFAULT') AS area_code,
                   COUNT(ti.id) AS dispatched_count,
                   COUNT(CASE WHEN ti.item_status = 'DELIVERED' THEN 1 END) AS delivered_count,
                   COUNT(CASE WHEN ti.item_status = 'FAILED' THEN 1 END) AS failed_count,
                   COUNT(att.id) AS total_attempts,
                   MIN(att.created_at) AS first_attempt_at,
                   MAX(att.created_at) AS last_attempt_at,
                   COUNT(CASE WHEN ti.item_status = 'DELIVERED' AND pod.id IS NULL THEN 1 END) AS missing_pod_count
            FROM driver d
            JOIN driver_task t ON t.driver_id = d.id AND t.station_id = ? AND t.service_date = ?
            JOIN driver_task_item ti ON ti.task_id = t.id
            LEFT JOIN driver_task_area dta ON dta.task_id = t.id
            LEFT JOIN delivery_area da ON da.id = dta.delivery_area_id
            LEFT JOIN delivery_attempt att ON att.task_item_id = ti.id
            LEFT JOIN proof_of_delivery pod ON pod.attempt_id = att.id
            GROUP BY d.id, d.driver_name, da.area_code
            """;

        return jdbc.query(sql, (rs, rowNum) -> {
            long driverId = rs.getLong("driver_id");
            String driverName = rs.getString("driver_name");
            String areaCode = rs.getString("area_code");
            int dispatchedCount = rs.getInt("dispatched_count");
            int deliveredCount = rs.getInt("delivered_count");
            int failedCount = rs.getInt("failed_count");
            int totalAttempts = rs.getInt("total_attempts");
            int missingPodCount = rs.getInt("missing_pod_count");

            java.sql.Timestamp firstAtt = rs.getTimestamp("first_attempt_at");
            java.sql.Timestamp lastAtt = rs.getTimestamp("last_attempt_at");
            
            double activeHours = 0.0;
            if (firstAtt != null) {
                long now = System.currentTimeMillis();
                long start = firstAtt.getTime();
                activeHours = Math.max(0.5, Math.round((now - start) / 3600000.0 * 10.0) / 10.0);
            }

            double baselineSph = areaCode.contains("Downtown") ? 20.0 : 12.0;
            double actualSph = ControlTowerPolicy.calculateActualSph(totalAttempts, activeHours);
            double efficiencyVariance = ControlTowerPolicy.calculateEfficiencyVariance(actualSph, baselineSph);
            String supervisionStatus = ControlTowerPolicy.evaluateSupervisionStatus(activeHours, totalAttempts, efficiencyVariance);

            return new DriverSupervisionItem(
                driverId, driverName, areaCode, dispatchedCount, deliveredCount, failedCount,
                activeHours, actualSph, baselineSph, efficiencyVariance, missingPodCount, supervisionStatus
            );
        }, stationId, serviceDate);
    }

    public List<DriverCapacityItem> driverCapacity(LocalDate serviceDate) {
        long stationId = station();

        // Query 1: Fetch active drivers for station with shift capacity (only driver + shift tables)
        String driverSql = """
            SELECT d.id AS driver_id, d.credential_id AS driver_code, d.driver_name,
                   COALESCE(ds.availability_status, 'UNAVAILABLE') AS availability_status,
                   COALESCE(ds.parcel_capacity, 200) AS capacity_limit
            FROM driver d
            LEFT JOIN driver_shift ds ON ds.driver_id = d.id AND ds.station_id = ? AND ds.service_date = ?
            WHERE d.home_station_id = ? AND d.status = 'ACTIVE'
            ORDER BY d.id
            """;

        List<DriverCapacityItem> drivers = jdbc.query(driverSql, (rs, rowNum) -> new DriverCapacityItem(
            rs.getLong("driver_id"),
            rs.getString("driver_code"),
            rs.getString("driver_name"),
            rs.getString("availability_status"),
            "VAN 标准货车",
            rs.getInt("capacity_limit"),
            0
        ), stationId, serviceDate, stationId);

        if (drivers.isEmpty()) {
            return List.of();
        }

        // Query 2: Fetch assigned parcel counts grouped by driver for the day's active tasks (no parcel/item table Join)
        String taskSql = """
            SELECT t.driver_id, COUNT(ti.id) AS assigned_count
            FROM driver_task t
            JOIN driver_task_item ti ON ti.task_id = t.id AND ti.item_status IN ('ASSIGNED','LOADED','OUT_FOR_DELIVERY','DELIVERED')
            WHERE t.station_id = ? AND t.service_date = ? AND t.status <> 'CANCELLED'
            GROUP BY t.driver_id
            """;

        Map<Long, Integer> assignedMap = new LinkedHashMap<>();
        jdbc.query(taskSql, rs -> {
            assignedMap.put(rs.getLong("driver_id"), rs.getInt("assigned_count"));
        }, stationId, serviceDate);

        // Java-side in-memory aggregation & assembly
        return drivers.stream()
            .map(d -> new DriverCapacityItem(
                d.driverId(),
                d.driverCode(),
                d.driverName(),
                d.status(),
                d.vehicleType(),
                d.capacityLimit(),
                assignedMap.getOrDefault(d.driverId(), 0)
            ))
            .toList();
    }

    public List<InboundDiscrepancyItem> inboundDiscrepancies(LocalDate serviceDate) {
        long stationId = station();
        String sql = """
            SELECT c.id AS case_id, c.case_no, c.case_type, c.status AS case_status,
                   p.tracking_no, m.external_manifest_no AS manifest_no,
                   c.description
            FROM operational_case c
            LEFT JOIN parcel p ON p.id = c.parcel_id
            LEFT JOIN inbound_manifest_item mi ON mi.parcel_id = p.id
            LEFT JOIN inbound_manifest m ON m.id = mi.manifest_id
            WHERE c.station_id = ? AND c.case_type IN ('INBOUND_MISSING','WRONG_STATION','PHYSICAL_DAMAGED','INBOUND_DISCREPANCY')
            ORDER BY c.id DESC
            """;
        return jdbc.query(sql, (rs, rowNum) -> {
            String caseType = rs.getString("case_type");
            String physicalStatus = "NOT_FOUND";
            String discrepancyType = "HIDDEN_MISSING";
            if ("WRONG_STATION".equalsIgnoreCase(caseType)) {
                physicalStatus = "MIS_ROUTED";
                discrepancyType = "WRONG_STATION";
            } else if ("PHYSICAL_DAMAGED".equalsIgnoreCase(caseType)) {
                physicalStatus = "DAMAGED";
                discrepancyType = "PHYSICAL_DAMAGED";
            }

            return new InboundDiscrepancyItem(
                String.valueOf(rs.getLong("case_id")),
                rs.getString("tracking_no") != null ? rs.getString("tracking_no") : "TRK-CASE-" + rs.getLong("case_id"),
                rs.getString("manifest_no") != null ? rs.getString("manifest_no") : "MNF-" + serviceDate.toString().replace("-", ""),
                physicalStatus,
                discrepancyType,
                rs.getString("case_no"),
                "RESOLVED".equalsIgnoreCase(rs.getString("case_status")) ? "RESOLVED" : "CASE_OPENED"
            );
        }, stationId);
    }

    private Stage stage(String code,int total,int complete,int blockers,String target){return new Stage(code,ControlTowerPolicy.stageStatus(total,complete,blockers),total,complete,blockers,ControlTowerPolicy.percent(total,complete),target);}
    private Metric metric(String code,int count,String target,String filter){return new Metric(code,count,target,filter);}
    private ActionItem action(String code,int count,String severity,String target,String filter){return new ActionItem(code,count,severity,target,filter);}
    private void addException(List<ExceptionItem> list,String code,int count,String severity,String target,String filter){if(count>0)list.add(new ExceptionItem(code,count,severity,target,filter));}
    private int count(String sql,Object... args){Integer value=jdbc.queryForObject(sql,Integer.class,args);return value==null?0:value;}
    private long station(){Long id=access.selectedStationId();if(id==null)throw new com.hf.easydelivery.common.exception.BizException("STATION.CONTEXT.REQUIRED","Station context is required");return id;}

    public record Snapshot(Map<String,Object> station,LocalDate serviceDate,OffsetDateTime generatedAt,List<Metric> metrics,List<Stage> stages,Capacity capacity,List<ExceptionItem> exceptions,List<ActionItem> actions){}
    public record Metric(String code,int count,String target,String filter){}
    public record Stage(String code,String status,int total,int completed,int blockers,int percent,String target){}
    public record Capacity(int availableDrivers,int total,int assigned,int remaining,int shortage){}
    public record ExceptionItem(String code,int count,String severity,String target,String filter){}
    public record ActionItem(String code,int count,String severity,String target,String filter){}
    public record DriverSupervisionItem(long driverId, String driverName, String areaCode, int dispatchedCount, int deliveredCount, int failedCount, double activeHours, double actualSph, double baselineSph, double efficiencyVariancePercent, int missingPodCount, String supervisionStatus){}
    public record DriverCapacityItem(long driverId, String driverCode, String driverName, String status, String vehicleType, int capacityLimit, int assignedCount){}
    public record InboundDiscrepancyItem(String id, String trackingNo, String manifestNo, String physicalStatus, String discrepancyType, String actionCaseNo, String actionStatus){}
}
