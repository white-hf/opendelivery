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
        int expected=count("SELECT COUNT(*) FROM parcel WHERE current_station_id=? AND promised_date=? AND status<>'CANCELLED'",stationId,serviceDate);
        int routed=count("SELECT COUNT(*) FROM parcel p JOIN waybill w ON w.id=p.waybill_id WHERE p.current_station_id=? AND p.promised_date=? AND w.resolved_station_id=? AND w.routing_status IN ('ROUTED','OVERRIDDEN')",stationId,serviceDate,stationId);
        int geocoded=count("SELECT COUNT(*) FROM parcel p JOIN waybill_geocode g ON g.waybill_id=p.waybill_id WHERE p.current_station_id=? AND p.promised_date=?",stationId,serviceDate);
        int areaMatched=count("SELECT COUNT(*) FROM parcel p WHERE p.current_station_id=? AND p.promised_date=? AND p.current_area_version_id IS NOT NULL",stationId,serviceDate);
        int assigned=count("SELECT COUNT(DISTINCT ti.parcel_id) FROM driver_task t JOIN driver_task_item ti ON ti.task_id=t.id AND ti.item_status IN ('ASSIGNED','LOADED','OUT_FOR_DELIVERY') WHERE t.station_id=? AND t.service_date=? AND t.status IN ('DRAFT','FROZEN','PUBLISHED','ACCEPTING','IN_PROGRESS')",stationId,serviceDate);
        int arrived=count("SELECT COUNT(*) FROM parcel WHERE current_station_id=? AND promised_date=? AND current_custody_type='STATION'",stationId,serviceDate);
        int scanned=count("SELECT COUNT(DISTINCT se.parcel_id) FROM scan_event se JOIN scan_session ss ON ss.id=se.session_id JOIN driver_task t ON t.id=ss.task_id WHERE t.station_id=? AND t.service_date=? AND se.result_code='EXPECTED'",stationId,serviceDate);
        int released=count("SELECT COUNT(DISTINCT ti.parcel_id) FROM driver_task t JOIN driver_task_item ti ON ti.task_id=t.id WHERE t.station_id=? AND t.service_date=? AND t.status IN ('ACCEPTING','IN_PROGRESS','CLOSED') AND ti.item_status IN ('LOADED','OUT_FOR_DELIVERY','DELIVERED','FAILED','RETURNED')",stationId,serviceDate);
        int out=count("SELECT COUNT(*) FROM parcel WHERE current_station_id=? AND promised_date=? AND status='OUT_FOR_DELIVERY'",stationId,serviceDate);
        int delivered=count("SELECT COUNT(*) FROM parcel WHERE current_station_id=? AND promised_date=? AND status='DELIVERED'",stationId,serviceDate);
        int failed=count("SELECT COUNT(*) FROM parcel WHERE current_station_id=? AND promised_date=? AND status IN ('DELIVERY_FAILED','RETURN_PENDING','RETURNED_TO_STATION')",stationId,serviceDate);
        int openCases=count("SELECT COUNT(*) FROM operational_case c WHERE c.status NOT IN ('RESOLVED','CLOSED') AND (c.station_id=? OR (c.station_id IS NULL AND c.parcel_id IN (SELECT id FROM parcel WHERE current_station_id=?)))",stationId,stationId);
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
}
