package com.hf.easydelivery.operations.arrival;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hf.easydelivery.common.exception.BizException;
import com.hf.easydelivery.config.OperationsAccess;
import com.hf.easydelivery.operations.arrival.persistence.ArrivalTripEntity;
import com.hf.easydelivery.operations.arrival.persistence.ArrivalTripRepository;
import com.hf.easydelivery.operations.arrival.persistence.HandlingUnitEntity;
import com.hf.easydelivery.operations.arrival.persistence.HandlingUnitRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Arrival-batch command service. Entity lifecycle goes through the JPA repositories
 * (persistence ADR); reporting queries and set-based INSERT…SELECT stay on JdbcTemplate
 * as documented escape hatches.
 */
@Service
@Profile("!memory")
public class PhysicalArrivalService {
    private final JdbcTemplate jdbc;
    private final OperationsAccess access;
    private final ObjectMapper mapper;
    private final ArrivalTripRepository tripRepo;
    private final HandlingUnitRepository unitRepo;

    public PhysicalArrivalService(JdbcTemplate jdbc, OperationsAccess access, ObjectMapper mapper,
                                  ArrivalTripRepository tripRepo, HandlingUnitRepository unitRepo) {
        this.jdbc=jdbc;this.access=access;this.mapper=mapper;this.tripRepo=tripRepo;this.unitRepo=unitRepo;
    }

    // Reporting read model (ADR escape hatch): aggregate SQL stays JDBC.
    public List<Map<String,Object>> trips(LocalDate serviceDate){long station=station();return jdbc.queryForList("""
            SELECT t.id,t.external_trip_no,t.vehicle_plate,t.seal_no,t.expected_at,t.arrived_at,t.status,t.note,
                   COUNT(DISTINCT u.id) unit_count,COALESCE(SUM(u.expected_piece_count),0) expected_piece_count,
                   COUNT(DISTINCT hp.parcel_id) linked_piece_count
            FROM arrival_trip t LEFT JOIN handling_unit u ON u.trip_id=t.id
            LEFT JOIN handling_unit_parcel hp ON hp.handling_unit_id=u.id
            WHERE t.station_id=? AND (DATE(COALESCE(t.expected_at,t.created_at))=? OR ? IS NULL)
            GROUP BY t.id ORDER BY COALESCE(t.expected_at,t.created_at),t.id
            """,station,serviceDate,serviceDate);}

    // Reporting read model (ADR escape hatch): coverage aggregates stay JDBC.
    public Map<String,Object> detail(long tripId){ArrivalTripEntity trip=trip(tripId);
        List<Map<String,Object>> units=jdbc.queryForList("""
                SELECT u.id,u.external_unit_no,u.unit_type,u.expected_piece_count,u.status,u.arrived_at,u.opened_at,
                       COUNT(DISTINCT hp.parcel_id) linked_piece_count,
                       COUNT(DISTINCT dt.driver_id) driver_count,
                       COUNT(DISTINCT dt.wave_id) wave_count,
                       (SELECT COUNT(*) FROM parcel pd WHERE pd.upstream_unit_no=u.external_unit_no) upstream_declared_count,
                       (SELECT COUNT(*) FROM parcel px WHERE px.upstream_unit_no=u.external_unit_no
                          AND NOT EXISTS(SELECT 1 FROM handling_unit_parcel hx WHERE hx.handling_unit_id=u.id AND hx.parcel_id=px.id)) exception_piece_count,
                       (SELECT COUNT(DISTINCT sx.parcel_id) FROM handling_unit_parcel hx
                          JOIN scan_event sx ON sx.parcel_id=hx.parcel_id AND sx.result_code='EXPECTED'
                          JOIN scan_session sn ON sn.id=sx.session_id AND sn.session_type='LOAD'
                          WHERE hx.handling_unit_id=u.id) scanned_piece_count
                FROM handling_unit u LEFT JOIN handling_unit_parcel hp ON hp.handling_unit_id=u.id
                LEFT JOIN driver_task_item dti ON dti.parcel_id=hp.parcel_id AND dti.item_status='ASSIGNED'
                LEFT JOIN driver_task dt ON dt.id=dti.task_id
                WHERE u.trip_id=? GROUP BY u.id ORDER BY u.id
                """,trip.getId());
        units.forEach(row->{long upstream=((Number)row.get("upstream_declared_count")).longValue();Object expected=row.get("expected_piece_count");
            row.put("declared_piece_count",upstream>0?upstream:expected==null?0L:((Number)expected).longValue());});
        return Map.of(
                "trip",jdbc.queryForMap("SELECT id,external_trip_no,vehicle_plate,seal_no,expected_at,arrived_at,status,note,version FROM arrival_trip WHERE id=?",trip.getId()),
                "units",units,
                "parcels",jdbc.queryForList("""
                        SELECT hp.handling_unit_id unit_id,p.id parcel_id,p.tracking_no,p.status parcel_status,hp.link_source,
                               dti.item_status,dt.id task_id,dt.task_code,dr.id driver_id,dr.driver_name
                        FROM handling_unit_parcel hp
                        JOIN parcel p ON p.id=hp.parcel_id
                        JOIN handling_unit hu ON hu.id=hp.handling_unit_id
                        LEFT JOIN driver_task_item dti ON dti.parcel_id=p.id AND dti.item_status='ASSIGNED'
                        LEFT JOIN driver_task dt ON dt.id=dti.task_id
                        LEFT JOIN driver dr ON dr.id=dt.driver_id
                        WHERE hu.trip_id=? ORDER BY hp.handling_unit_id,p.tracking_no
                        """,trip.getId()),
                "unlinkedDeclarations",jdbc.queryForList("""
                        SELECT u.id unit_id,u.external_unit_no,p.id parcel_id,p.tracking_no,p.status parcel_status,
                               p.current_station_id,s.station_code
                        FROM handling_unit u
                        JOIN parcel p ON p.upstream_unit_no=u.external_unit_no
                        LEFT JOIN station s ON s.id=p.current_station_id
                        WHERE u.trip_id=? AND NOT EXISTS(SELECT 1 FROM handling_unit_parcel hp WHERE hp.handling_unit_id=u.id AND hp.parcel_id=p.id)
                        ORDER BY u.id,p.tracking_no
                        """,trip.getId()));}

    @Transactional public Map<String,Object> createTrip(TripRequest body,HttpServletRequest http){long station=station();
        String requested=body.externalTripNo();String no;ArrivalTripEntity trip;
        if(requested==null||requested.isBlank()){
            LocalDate day=body.expectedAt()==null?LocalDate.now():body.expectedAt().toLocalDate();
            // Escape hatch (ADR): vendor DATE() function for the daily sequence lookup
            String code=jdbc.queryForObject("SELECT station_code FROM station WHERE id=?",String.class,station);
            Long existing=jdbc.queryForObject("SELECT COUNT(*) FROM arrival_trip WHERE station_id=? AND DATE(COALESCE(expected_at,created_at))=?",Long.class,station,day);
            no=null;trip=null;
            for(long sequence=(existing==null?0:existing)+1;sequence<=99;sequence++){
                String candidate=ArrivalLinkagePolicy.batchNo(code,day,sequence);
                try{trip=tripRepo.save(new ArrivalTripEntity(station,candidate,body.vehiclePlate(),body.sealNo(),body.expectedAt(),body.note()));no=candidate;break;}
                catch(DataIntegrityViolationException ignored){}
            }
            if(no==null||trip==null)throw new BizException("ARRIVAL.TRIP.EXISTS","Cannot allocate an arrival batch number for the day");
        }else{
            no=requested.trim();
            try{trip=tripRepo.save(new ArrivalTripEntity(station,no,body.vehiclePlate(),body.sealNo(),body.expectedAt(),body.note()));}
            catch(DataIntegrityViolationException ex){throw new BizException("ARRIVAL.TRIP.EXISTS","Trip number already exists at selected station");}
        }
        int defaultUnits=0;
        for(String unitNo:ArrivalLinkagePolicy.defaultUnitNumbers(no,ArrivalLinkagePolicy.DEFAULT_UNIT_COUNT)){
            // Escape hatch (ADR): dialect upsert for generated default units
            defaultUnits+=jdbc.update("INSERT IGNORE INTO handling_unit(trip_id,station_id,external_unit_no,unit_type) VALUES (?,?,?,'PALLET')",trip.getId(),station,unitNo);
        }
        audit(http,station,"ARRIVAL_TRIP_CREATED","ARRIVAL_TRIP",trip.getId(),body.note(),Map.of("externalTripNo",no,"defaultUnitsCreated",defaultUnits));
        return detail(trip.getId());}

    @Transactional public Map<String,Object> moveTrip(long tripId,StateRequest body,HttpServletRequest http){
        ArrivalTripEntity trip=tripForUpdate(tripId);
        String from=trip.getStatus();
        String target=required(body.targetStatus(),"targetStatus").toUpperCase();
        if(!PhysicalArrivalPolicy.canMoveTrip(from,target))throw new BizException("ARRIVAL.STATE.INVALID","Trip cannot move from "+from+" to "+target);
        if("ARRIVED".equals(target))trip.arrive();else trip.moveTo(target);
        audit(http,trip.getStationId(),"ARRIVAL_TRIP_"+target,"ARRIVAL_TRIP",trip.getId(),body.reason(),Map.of("from",from,"to",target));
        return detail(trip.getId());}

    @Transactional public Map<String,Object> createUnit(long tripId,UnitRequest body,HttpServletRequest http){
        ArrivalTripEntity trip=tripForUpdate(tripId);
        if(List.of("CLOSED","CANCELLED").contains(trip.getStatus()))throw new BizException("ARRIVAL.STATE.INVALID","Cannot add a unit to a closed trip");
        String no=required(body.externalUnitNo(),"externalUnitNo");
        String type=required(body.unitType(),"unitType").toUpperCase();
        if(!List.of("PALLET","CAGE","BAG","LOOSE").contains(type))throw new BizException("PARAM.INVALID","Unsupported unitType");
        HandlingUnitEntity unit;
        try{unit=unitRepo.save(new HandlingUnitEntity(trip.getId(),trip.getStationId(),no,type,body.expectedPieceCount()));}
        catch(DataIntegrityViolationException ex){throw new BizException("ARRIVAL.UNIT.EXISTS","Handling-unit label already exists at selected station");}
        for(String tracking:body.trackingNumbers()==null?List.<String>of():body.trackingNumbers()){
            List<Long> parcels=jdbc.query("SELECT id FROM parcel WHERE tracking_no=? AND current_station_id=?",(rs,n)->rs.getLong(1),tracking,trip.getStationId());
            if(parcels.isEmpty())throw new BizException("ARRIVAL.PARCEL.INVALID","Parcel is unknown or belongs to another station: "+tracking);
            // Escape hatch (ADR): dialect upsert for operator-typed links
            jdbc.update("INSERT IGNORE INTO handling_unit_parcel(handling_unit_id,parcel_id,link_source) VALUES (?,?,'OPERATOR')",unit.getId(),parcels.get(0));
        }
        // Escape hatch (ADR): set-based INSERT…SELECT for upstream-label auto-link
        int upstreamLinked=jdbc.update("INSERT IGNORE INTO handling_unit_parcel(handling_unit_id,parcel_id,link_source) SELECT ?,id,'UPSTREAM' FROM parcel WHERE current_station_id=? AND upstream_unit_no=?",unit.getId(),trip.getStationId(),no);
        audit(http,trip.getStationId(),"HANDLING_UNIT_CREATED","HANDLING_UNIT",unit.getId(),body.reason(),Map.of("tripId",trip.getId(),"unitNo",no,"upstreamLinked",upstreamLinked));
        return detail(trip.getId());}

    @Transactional public Map<String,Object> areaFill(long unitId,AreaFillRequest body,HttpServletRequest http){
        HandlingUnitEntity unit=unitForUpdate(unitId);
        if(body.areaVersionIds()==null||body.areaVersionIds().isEmpty()){
            jdbc.update("DELETE FROM handling_unit_parcel WHERE handling_unit_id=? AND link_source='AREA_PLAN'", unit.getId());
            audit(http,unit.getStationId(),"HANDLING_UNIT_AREA_CLEARED","HANDLING_UNIT",unit.getId(),body.reason(),Map.of("cleared",true));
            return detail(unit.getTripId());
        }
        for(Long versionId:body.areaVersionIds()){
            Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM delivery_area_version av JOIN delivery_area a ON a.id=av.delivery_area_id WHERE av.id=? AND a.station_id=? AND a.status='ACTIVE' AND av.status='PUBLISHED'",Integer.class,versionId,unit.getStationId());
            if(n==null||n==0)throw new BizException("AREA.NOT.AVAILABLE","Published area does not belong to selected station");
        }
        jdbc.update("DELETE FROM handling_unit_parcel WHERE handling_unit_id=? AND link_source='AREA_PLAN'", unit.getId());
        int linked=0;
        for(Long versionId:body.areaVersionIds()){
            // Escape hatch (ADR): set-based INSERT…SELECT over the denormalized area projection
            linked+=jdbc.update("""
                    INSERT IGNORE INTO handling_unit_parcel(handling_unit_id,parcel_id,link_source)
                    SELECT ?,p.id,'AREA_PLAN' FROM parcel p
                    WHERE p.current_area_version_id=? AND p.current_station_id=?
                      AND p.status NOT IN ('DELIVERED','RETURNED_TO_UPSTREAM','CANCELLED','LOST')
                      AND NOT EXISTS(SELECT 1 FROM handling_unit_parcel other JOIN handling_unit ou ON ou.id=other.handling_unit_id
                                     WHERE other.parcel_id=p.id AND ou.trip_id=? AND other.handling_unit_id<>?)
                    """,unit.getId(),versionId,unit.getStationId(),unit.getTripId(),unit.getId());
        }
        audit(http,unit.getStationId(),"HANDLING_UNIT_AREA_FILLED","HANDLING_UNIT",unit.getId(),body.reason(),Map.of("areaVersionIds",body.areaVersionIds(),"linkedCount",linked));
        return detail(unit.getTripId());}

    @Transactional public Map<String,Object> moveUnit(long unitId,StateRequest body,HttpServletRequest http){
        HandlingUnitEntity unit=unitForUpdate(unitId);
        String from=unit.getStatus();
        String target=required(body.targetStatus(),"targetStatus").toUpperCase();
        if(!PhysicalArrivalPolicy.canMoveUnit(from,target))throw new BizException("ARRIVAL.STATE.INVALID","Handling unit cannot move from "+from+" to "+target);
        unit.moveTo(target);
        audit(http,unit.getStationId(),"HANDLING_UNIT_"+target,"HANDLING_UNIT",unit.getId(),body.reason(),Map.of("from",from,"to",target));
        return detail(unit.getTripId());}

    private ArrivalTripEntity trip(long id){ArrivalTripEntity trip=tripRepo.findById(id).orElseThrow(()->new BizException("ARRIVAL.TRIP.NOT_FOUND","Arrival trip not found"));access.requireStation(trip.getStationId());return trip;}
    private ArrivalTripEntity tripForUpdate(long id){ArrivalTripEntity trip=tripRepo.findByIdForUpdate(id).orElseThrow(()->new BizException("ARRIVAL.TRIP.NOT_FOUND","Arrival trip not found"));access.requireStation(trip.getStationId());return trip;}
    private HandlingUnitEntity unitForUpdate(long id){HandlingUnitEntity unit=unitRepo.findByIdForUpdate(id).orElseThrow(()->new BizException("ARRIVAL.UNIT.NOT_FOUND","Handling unit not found"));access.requireStation(unit.getStationId());return unit;}
    private long station(){Long id=access.selectedStationId();if(id==null)throw new BizException("STATION.CONTEXT.REQUIRED","Station context is required");return id;}
    private String required(String value,String field){if(value==null||value.isBlank())throw new BizException("PARAM.INVALID",field+" is required");return value.trim();}
    private void audit(HttpServletRequest request,long station,String action,String type,long id,String reason,Object after){jdbc.update("INSERT INTO operation_audit_log(operator_user_id,actor_type,actor_id,station_id,action_code,resource_type,resource_id,outcome,reason_text,after_json,request_id,occurred_at) VALUES (?,'OPERATOR',?,?, ?,?,?, 'SUCCESS',?,CAST(? AS JSON),?,CURRENT_TIMESTAMP(3))",operator(request),operator(request),station,action,type,String.valueOf(id),reason,json(after),request.getHeader("X-Request-Id"));}
    private Long operator(HttpServletRequest request){return request.getAttribute("operatorUserId") instanceof Long id?id:null;}
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException(ex);}}
    public record TripRequest(String externalTripNo,String vehiclePlate,String sealNo,LocalDateTime expectedAt,String note){}
    public record UnitRequest(String externalUnitNo,String unitType,Integer expectedPieceCount,List<String> trackingNumbers,String reason){}
    public record AreaFillRequest(List<Long> areaVersionIds,String reason){}
    public record StateRequest(String targetStatus,String reason){}
}
