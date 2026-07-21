package com.hf.easydelivery.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hf.easydelivery.common.exception.BizException;
import com.hf.easydelivery.config.OperationsAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Profile("!memory")
public class PhysicalArrivalService {
    private final JdbcTemplate jdbc; private final OperationsAccess access; private final ObjectMapper mapper;
    public PhysicalArrivalService(JdbcTemplate jdbc, OperationsAccess access, ObjectMapper mapper){this.jdbc=jdbc;this.access=access;this.mapper=mapper;}

    public List<Map<String,Object>> trips(LocalDate serviceDate){long station=station();return jdbc.queryForList("""
            SELECT t.id,t.external_trip_no,t.vehicle_plate,t.seal_no,t.expected_at,t.arrived_at,t.status,t.note,
                   COUNT(DISTINCT u.id) unit_count,COALESCE(SUM(u.expected_piece_count),0) expected_piece_count,
                   COUNT(DISTINCT hp.parcel_id) linked_piece_count
            FROM arrival_trip t LEFT JOIN handling_unit u ON u.trip_id=t.id
            LEFT JOIN handling_unit_parcel hp ON hp.handling_unit_id=u.id
            WHERE t.station_id=? AND (DATE(COALESCE(t.expected_at,t.created_at))=? OR ? IS NULL)
            GROUP BY t.id ORDER BY COALESCE(t.expected_at,t.created_at),t.id
            """,station,serviceDate,serviceDate);}

    public Map<String,Object> detail(long tripId){Trip trip=trip(tripId,false);return Map.of(
            "trip",jdbc.queryForMap("SELECT id,external_trip_no,vehicle_plate,seal_no,expected_at,arrived_at,status,note,version FROM arrival_trip WHERE id=?",trip.id()),
            "units",jdbc.queryForList("""
                    SELECT u.id,u.external_unit_no,u.unit_type,u.expected_piece_count,u.status,u.arrived_at,u.opened_at,
                           COUNT(DISTINCT hp.parcel_id) linked_piece_count,COUNT(DISTINCT dt.driver_id) driver_count,
                           COUNT(DISTINCT dt.wave_id) wave_count
                    FROM handling_unit u LEFT JOIN handling_unit_parcel hp ON hp.handling_unit_id=u.id
                    LEFT JOIN driver_task_item dti ON dti.parcel_id=hp.parcel_id AND dti.item_status='ASSIGNED'
                    LEFT JOIN driver_task dt ON dt.id=dti.task_id
                    WHERE u.trip_id=? GROUP BY u.id ORDER BY u.id
                    """,trip.id()));}

    @Transactional public Map<String,Object> createTrip(TripRequest body,HttpServletRequest http){long station=station();String no=required(body.externalTripNo(),"externalTripNo");GeneratedKeyHolder keys=new GeneratedKeyHolder();try{jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO arrival_trip(station_id,external_trip_no,vehicle_plate,seal_no,expected_at,note) VALUES (?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);ps.setLong(1,station);ps.setString(2,no);ps.setString(3,body.vehiclePlate());ps.setString(4,body.sealNo());ps.setObject(5,body.expectedAt());ps.setString(6,body.note());return ps;},keys);}catch(DataIntegrityViolationException ex){throw new BizException("ARRIVAL.TRIP.EXISTS","Trip number already exists at selected station");}long id=keys.getKey().longValue();audit(http,station,"ARRIVAL_TRIP_CREATED","ARRIVAL_TRIP",id,body.note(),Map.of("externalTripNo",no));return detail(id);}

    @Transactional public Map<String,Object> moveTrip(long tripId,StateRequest body,HttpServletRequest http){Trip trip=trip(tripId,true);String target=required(body.targetStatus(),"targetStatus").toUpperCase();if(!PhysicalArrivalPolicy.canMoveTrip(trip.status(),target))throw new BizException("ARRIVAL.STATE.INVALID","Trip cannot move from "+trip.status()+" to "+target);jdbc.update("UPDATE arrival_trip SET status=?,arrived_at=CASE WHEN ?='ARRIVED' THEN CURRENT_TIMESTAMP(3) ELSE arrived_at END,version=version+1 WHERE id=?",target,target,trip.id());audit(http,trip.stationId(),"ARRIVAL_TRIP_"+target,"ARRIVAL_TRIP",trip.id(),body.reason(),Map.of("from",trip.status(),"to",target));return detail(trip.id());}

    @Transactional public Map<String,Object> createUnit(long tripId,UnitRequest body,HttpServletRequest http){Trip trip=trip(tripId,true);if(List.of("CLOSED","CANCELLED").contains(trip.status()))throw new BizException("ARRIVAL.STATE.INVALID","Cannot add a unit to a closed trip");String no=required(body.externalUnitNo(),"externalUnitNo");String type=required(body.unitType(),"unitType").toUpperCase();if(!List.of("PALLET","CAGE","BAG","LOOSE").contains(type))throw new BizException("PARAM.INVALID","Unsupported unitType");GeneratedKeyHolder keys=new GeneratedKeyHolder();try{jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO handling_unit(trip_id,station_id,external_unit_no,unit_type,expected_piece_count) VALUES (?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);ps.setLong(1,trip.id());ps.setLong(2,trip.stationId());ps.setString(3,no);ps.setString(4,type);if(body.expectedPieceCount()==null)ps.setNull(5,java.sql.Types.INTEGER);else ps.setInt(5,body.expectedPieceCount());return ps;},keys);}catch(DataIntegrityViolationException ex){throw new BizException("ARRIVAL.UNIT.EXISTS","Handling-unit label already exists at selected station");}long id=keys.getKey().longValue();for(String tracking:body.trackingNumbers()==null?List.<String>of():body.trackingNumbers()){List<Long> parcels=jdbc.query("SELECT id FROM parcel WHERE tracking_no=? AND current_station_id=?",(rs,n)->rs.getLong(1),tracking,trip.stationId());if(parcels.isEmpty())throw new BizException("ARRIVAL.PARCEL.INVALID","Parcel is unknown or belongs to another station: "+tracking);jdbc.update("INSERT IGNORE INTO handling_unit_parcel(handling_unit_id,parcel_id,link_source) VALUES (?,?,'OPERATOR')",id,parcels.get(0));}audit(http,trip.stationId(),"HANDLING_UNIT_CREATED","HANDLING_UNIT",id,body.reason(),Map.of("tripId",trip.id(),"unitNo",no));return detail(trip.id());}

    @Transactional public Map<String,Object> moveUnit(long unitId,StateRequest body,HttpServletRequest http){Unit unit=unit(unitId,true);String target=required(body.targetStatus(),"targetStatus").toUpperCase();if(!PhysicalArrivalPolicy.canMoveUnit(unit.status(),target))throw new BizException("ARRIVAL.STATE.INVALID","Handling unit cannot move from "+unit.status()+" to "+target);jdbc.update("UPDATE handling_unit SET status=?,arrived_at=CASE WHEN ?='ARRIVED' THEN CURRENT_TIMESTAMP(3) ELSE arrived_at END,opened_at=CASE WHEN ?='OPENED' THEN CURRENT_TIMESTAMP(3) ELSE opened_at END,version=version+1 WHERE id=?",target,target,target,unit.id());audit(http,unit.stationId(),"HANDLING_UNIT_"+target,"HANDLING_UNIT",unit.id(),body.reason(),Map.of("from",unit.status(),"to",target));return detail(unit.tripId());}

    private Trip trip(long id,boolean lock){List<Trip> rows=jdbc.query("SELECT id,station_id,status FROM arrival_trip WHERE id=?"+(lock?" FOR UPDATE":""),(rs,n)->new Trip(rs.getLong(1),rs.getLong(2),rs.getString(3)),id);if(rows.isEmpty())throw new BizException("ARRIVAL.TRIP.NOT_FOUND","Arrival trip not found");access.requireStation(rows.get(0).stationId());return rows.get(0);}
    private Unit unit(long id,boolean lock){List<Unit> rows=jdbc.query("SELECT id,trip_id,station_id,status FROM handling_unit WHERE id=?"+(lock?" FOR UPDATE":""),(rs,n)->new Unit(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getString(4)),id);if(rows.isEmpty())throw new BizException("ARRIVAL.UNIT.NOT_FOUND","Handling unit not found");access.requireStation(rows.get(0).stationId());return rows.get(0);}
    private long station(){Long id=access.selectedStationId();if(id==null)throw new BizException("STATION.CONTEXT.REQUIRED","Station context is required");return id;}
    private String required(String value,String field){if(value==null||value.isBlank())throw new BizException("PARAM.INVALID",field+" is required");return value.trim();}
    private void audit(HttpServletRequest request,long station,String action,String type,long id,String reason,Object after){jdbc.update("INSERT INTO operation_audit_log(operator_user_id,actor_type,actor_id,station_id,action_code,resource_type,resource_id,outcome,reason_text,after_json,request_id,occurred_at) VALUES (?,'OPERATOR',?,?, ?,?,?, 'SUCCESS',?,CAST(? AS JSON),?,CURRENT_TIMESTAMP(3))",operator(request),operator(request),station,action,type,String.valueOf(id),reason,json(after),request.getHeader("X-Request-Id"));}
    private Long operator(HttpServletRequest request){return request.getAttribute("operatorUserId") instanceof Long id?id:null;}
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException(ex);}}
    private record Trip(long id,long stationId,String status){} private record Unit(long id,long tripId,long stationId,String status){}
    public record TripRequest(String externalTripNo,String vehiclePlate,String sealNo,LocalDateTime expectedAt,String note){}
    public record UnitRequest(String externalUnitNo,String unitType,Integer expectedPieceCount,List<String> trackingNumbers,String reason){}
    public record StateRequest(String targetStatus,String reason){}
}
