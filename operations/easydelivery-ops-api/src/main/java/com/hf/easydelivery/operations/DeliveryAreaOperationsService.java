package com.hf.easydelivery.operations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hf.easydelivery.common.exception.BizException;
import com.hf.easydelivery.config.OperationsAccess;
import com.hf.easydelivery.operations.shared.AreaMembershipService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Profile("!memory")
public class DeliveryAreaOperationsService {
    private final JdbcTemplate jdbc;
    private final OperationsAccess access;
    private final ObjectMapper mapper;
    private final AreaMembershipService areaMembership;

    public DeliveryAreaOperationsService(JdbcTemplate jdbc, OperationsAccess access, ObjectMapper mapper,
                                         AreaMembershipService areaMembership) {
        this.jdbc=jdbc; this.access=access; this.mapper=mapper; this.areaMembership=areaMembership;
    }

    public List<Map<String,Object>> areas() {
        Long stationId=requireStation();
        return jdbc.queryForList("""
                SELECT a.id,a.area_code,a.area_name,a.area_level,a.status,
                       a.id AS version_id, 1 AS version_no, 'PUBLISHED' AS version_status,
                       ST_AsGeoJSON(a.boundary) geo_json,
                       p.driver_names AS primary_driver_name
                FROM delivery_area a
                LEFT JOIN (
                  SELECT delivery_area_id, GROUP_CONCAT(d.driver_name ORDER BY pref.priority ASC SEPARATOR ', ') AS driver_names
                  FROM driver_area_preference pref
                  JOIN driver d ON d.id = pref.driver_id
                  WHERE pref.status = 'ACTIVE'
                  GROUP BY delivery_area_id
                ) p ON p.delivery_area_id = a.id
                WHERE a.station_id=? ORDER BY a.area_level,a.area_code
                """,stationId);
    }

    public List<Map<String,Object>> versions(long areaId) {
        AreaRow area=area(areaId,false);
        return jdbc.queryForList("""
                SELECT id, 1 AS version_no, 'PUBLISHED' AS status, ST_AsGeoJSON(boundary) geo_json,
                       created_at
                FROM delivery_area WHERE id=?
                """,area.id());
    }

    public List<Map<String,Object>> driverPreferences(long areaId) {
        area(areaId,false);
        return jdbc.queryForList("""
                SELECT p.id,p.driver_id,d.credential_id AS driver_code,d.driver_name,p.priority,p.status,
                       p.effective_from,p.effective_to,p.created_at
                FROM driver_area_preference p JOIN driver d ON d.id=p.driver_id
                WHERE p.delivery_area_id=? ORDER BY (p.status='ACTIVE') DESC,p.priority,d.driver_name
                """,areaId);
    }

    @Transactional
    public Map<String,Object> saveDriverPreference(long areaId,DriverPreferenceRequest request,HttpServletRequest http) {
        AreaRow area=area(areaId,true);
        DeliveryAreaInputPolicy.preference(request.priority(),request.effectiveFrom(),request.effectiveTo());
        Integer driverCount=jdbc.queryForObject("SELECT COUNT(*) FROM driver WHERE id=? AND home_station_id=? AND status='ACTIVE'",
                Integer.class,request.driverId(),area.stationId());
        if(driverCount==null||driverCount==0) throw new BizException("AREA.DRIVER.INVALID","Driver is not active in the selected station");
        jdbc.update("""
                INSERT INTO driver_area_preference(driver_id,delivery_area_id,priority,status,effective_from,effective_to,created_by)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?)
                ON DUPLICATE KEY UPDATE priority=VALUES(priority),status='ACTIVE',effective_from=VALUES(effective_from),
                  effective_to=VALUES(effective_to),created_by=VALUES(created_by),updated_at=CURRENT_TIMESTAMP(3)
                """,request.driverId(),areaId,request.priority()==null?100:request.priority(),request.effectiveFrom(),request.effectiveTo(),operator(http));
        audit(http,area.stationId(),"DRIVER_AREA_PREFERENCE_SAVED",areaId,"SUCCESS",required(request.reason(),"reason"),null,
                Map.of("driverId",request.driverId(),"priority",request.priority()==null?100:request.priority()));
        return Map.of("areaId",areaId,"driverId",request.driverId(),"status","ACTIVE");
    }

    @Transactional
    public Map<String,Object> deleteDriverPreference(long areaId, long preferenceId, HttpServletRequest http) {
        AreaRow area = area(areaId, true);
        jdbc.update("UPDATE driver_area_preference SET status='INACTIVE', updated_at=CURRENT_TIMESTAMP(3) WHERE id=? AND delivery_area_id=?", preferenceId, areaId);
        audit(http, area.stationId(), "DRIVER_AREA_PREFERENCE_DELETED", areaId, "SUCCESS", "Deactivated driver preference", null, Map.of("preferenceId", preferenceId));
        return Map.of("areaId", areaId, "preferenceId", preferenceId, "status", "INACTIVE");
    }

    @Transactional
    public AreaMatchResult matchParcel(long parcelId,ParcelLocationRequest request,HttpServletRequest http) {
        Long stationId=requireStation();
        List<ParcelRow> parcels=jdbc.query("""
                SELECT p.id,p.waybill_id FROM parcel p WHERE p.id=? AND p.current_station_id=?
                """,(rs,n)->new ParcelRow(rs.getLong(1),rs.getLong(2)),parcelId,stationId);
        if(parcels.isEmpty()) throw new BizException("PARCEL.NOT.FOUND","Parcel not found in selected station");
        DeliveryAreaInputPolicy.coordinates(request.longitude(),request.latitude(),request.confidence());
        ParcelRow parcel=parcels.get(0);
        jdbc.update("""
                INSERT INTO waybill_geocode(waybill_id,delivery_point,provider_code,precision_code,confidence,normalized_address,geocoded_at)
                VALUES (?,ST_SRID(POINT(?,?),4326),?,?,?,?,CURRENT_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE delivery_point=VALUES(delivery_point),provider_code=VALUES(provider_code),
                  precision_code=VALUES(precision_code),confidence=VALUES(confidence),normalized_address=VALUES(normalized_address),
                  geocoded_at=CURRENT_TIMESTAMP(3),version=version+1
                """,parcel.waybillId(),request.longitude(),request.latitude(),required(request.providerCode(),"providerCode"),
                required(request.precisionCode(),"precisionCode"),request.confidence(),request.normalizedAddress());
        List<MatchRow> matches=jdbc.query("""
                SELECT a.id,a.area_code
                FROM delivery_area a
                WHERE a.station_id=? AND a.status='ACTIVE' AND a.boundary IS NOT NULL
                  AND ST_Intersects(a.boundary,ST_SRID(POINT(?,?),4326))
                ORDER BY a.area_level DESC,a.area_code LIMIT 1
                """,(rs,n)->new MatchRow(rs.getLong(1),rs.getString(2)),stationId,request.longitude(),request.latitude());
        if(matches.isEmpty()) throw new BizException("AREA.MATCH.NOT.FOUND","No active delivery area contains the parcel location");
        MatchRow match=matches.get(0);
        jdbc.update("UPDATE parcel_area_assignment SET ended_at=CURRENT_TIMESTAMP(3) WHERE parcel_id=? AND ended_at IS NULL",parcelId);
        jdbc.update("""
                INSERT INTO parcel_area_assignment(parcel_id,delivery_area_id,assignment_source,assignment_reason,assigned_by)
                VALUES (?,?,'GEO_POLYGON',?,?)
                """,parcelId,match.areaId(),request.reason(),operator(http));
        jdbc.update("UPDATE parcel SET current_area_id=? WHERE id=?",match.areaId(),parcelId);
        audit(http,stationId,"PARCEL_AREA_MATCHED",match.areaId(),"SUCCESS",required(request.reason(),"reason"),null,
                Map.of("parcelId",parcelId,"areaId",match.areaId()));
        return new AreaMatchResult(parcelId,match.areaId(),match.areaId(),match.areaCode(),1,"GEO_POLYGON");
    }

    @Transactional
    public AreaMatchResult overrideParcelArea(long parcelId,ParcelAreaOverrideRequest request,HttpServletRequest http) {
        Long stationId=requireStation();
        List<ParcelRow> parcels=jdbc.query("SELECT id,waybill_id FROM parcel WHERE id=? AND current_station_id=? FOR UPDATE",(rs,n)->new ParcelRow(rs.getLong(1),rs.getLong(2)),parcelId,stationId);
        if(parcels.isEmpty())throw new BizException("PARCEL.NOT.FOUND","Parcel not found at selected station");
        long targetAreaId = request.areaVersionId();
        List<MatchRow> areas=jdbc.query("""
                SELECT a.id,a.area_code FROM delivery_area a
                WHERE a.id=? AND a.station_id=? AND a.status='ACTIVE'
                """,(rs,n)->new MatchRow(rs.getLong(1),rs.getString(2)),targetAreaId,stationId);
        if(areas.isEmpty())throw new BizException("AREA.NOT.AVAILABLE","Active area does not belong to selected station");
        String reason=required(request.reason(),"reason");MatchRow area=areas.get(0);
        jdbc.update("UPDATE parcel_area_assignment SET ended_at=CURRENT_TIMESTAMP(3) WHERE parcel_id=? AND ended_at IS NULL",parcelId);
        jdbc.update("INSERT INTO parcel_area_assignment(parcel_id,delivery_area_id,assignment_source,assignment_reason,assigned_by) VALUES (?,?,'MANUAL_OVERRIDE',?,?)",parcelId,area.areaId(),reason,operator(http));
        jdbc.update("UPDATE parcel SET current_area_id=? WHERE id=?",area.areaId(),parcelId);
        audit(http,stationId,"PARCEL_AREA_OVERRIDDEN",area.areaId(),"SUCCESS",reason,null,Map.of("parcelId",parcelId,"areaId",area.areaId()));
        return new AreaMatchResult(parcelId,area.areaId(),area.areaId(),area.areaCode(),1,"MANUAL_OVERRIDE");
    }

    @Transactional
    public Map<String,Object> recomputeAreas(AreaRecomputeRequest request,HttpServletRequest http) {
        Long stationId=requireStation();
        List<Long> parcelIds;
        if(request!=null&&request.parcelIds()!=null&&!request.parcelIds().isEmpty()) {
            parcelIds=request.parcelIds();
        } else {
            parcelIds=jdbc.query("""
                    SELECT p.id FROM parcel p JOIN waybill_geocode g ON g.waybill_id=p.waybill_id
                    WHERE p.current_station_id=? AND p.current_area_id IS NULL
                      AND p.status NOT IN ('DELIVERED','RETURNED_TO_UPSTREAM','CANCELLED','LOST')
                    ORDER BY p.id LIMIT 500
                    """,(rs,n)->rs.getLong(1),stationId);
        }
        List<Long> own=parcelIds;
        if(request!=null&&request.parcelIds()!=null&&!request.parcelIds().isEmpty()) {
            Object[] args=new Object[parcelIds.size()+1];args[0]=stationId;
            for(int i=0;i<parcelIds.size();i++) args[i+1]=parcelIds.get(i);
            own=jdbc.query("SELECT id FROM parcel WHERE current_station_id=? AND id IN ("
                    +String.join(",",java.util.Collections.nCopies(parcelIds.size(),"?"))+")",(rs,n)->rs.getLong(1),args);
        }
        int matched=0;
        for(Long parcelId:own) {
            if(areaMembership.matchFromGeocode(parcelId,stationId,"bulk recompute",operator(http))!=null) matched++;
        }
        audit(http,stationId,"PARCEL_AREA_RECOMPUTED",0L,"SUCCESS",request==null?null:request.reason(),null,
                Map.of("requested",parcelIds.size(),"matched",matched));
        return Map.of("requested",parcelIds.size(),"matched",matched,"unmatched",parcelIds.size()-matched);
    }

    @Transactional
    public VersionResult create(CreateRequest request,HttpServletRequest http) {
        Long stationId=requireStation();
        String code=required(request.areaCode(),"areaCode").toUpperCase();
        List<Long> driverIds = request.driverIds();
        if ((driverIds == null || driverIds.isEmpty()) && request.primaryDriverId() != null) {
            driverIds = List.of(request.primaryDriverId());
        }
        if (driverIds == null || driverIds.isEmpty()) {
            throw new BizException("AREA.DRIVER.REQUIRED", "新增区域必须指定至少一名责任司机");
        }

        String normalized = DeliveryAreaGeoJson.normalize(mapper, request.geoJson());
        try {
            jdbc.update("""
                    INSERT INTO delivery_area(station_id,area_code,area_name,area_level,boundary,geojson_snapshot)
                    VALUES (?,?,?,?,ST_GeomFromGeoJSON(?,1,4326),CAST(? AS JSON))
                    """,
                    stationId,code,required(request.areaName(),"areaName"),request.areaLevel()==null?1:request.areaLevel(),normalized,normalized);
        } catch(DataAccessException ex) {
            throw new BizException("AREA.GEOMETRY.INVALID","Delivery area geometry is not a valid WGS84 polygon");
        }
        long areaId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);

        for (int i = 0; i < driverIds.size(); i++) {
            Long driverId = driverIds.get(i);
            jdbc.update("""
                    INSERT INTO driver_area_preference(driver_id,delivery_area_id,priority,status,created_by)
                    VALUES (?, ?, ?, 'ACTIVE', ?)
                    ON DUPLICATE KEY UPDATE priority=VALUES(priority),status='ACTIVE',created_by=VALUES(created_by)
                    """, driverId, areaId, i + 1, operator(http));
        }

        audit(http,stationId,"DELIVERY_AREA_CREATED",areaId,"SUCCESS",request.changeReason(),null,
                Map.of("areaCode",code,"areaId",areaId,"driverIds",driverIds));
        return new VersionResult(areaId,areaId,1,"PUBLISHED");
    }

    @Transactional
    public VersionResult createVersion(long areaId,VersionRequest request,HttpServletRequest http) {
        AreaRow area=area(areaId,true);
        String normalized = DeliveryAreaGeoJson.normalize(mapper, request.geoJson());
        jdbc.update("UPDATE delivery_area SET boundary=ST_GeomFromGeoJSON(?,1,4326), geojson_snapshot=CAST(? AS JSON), version=version+1 WHERE id=?", normalized, normalized, areaId);
        audit(http,area.stationId(),"DELIVERY_AREA_VERSION_UPDATED",areaId,"SUCCESS",request.changeReason(),null,
                Map.of("areaId",areaId));
        return new VersionResult(areaId,areaId,1,"PUBLISHED");
    }

    @Transactional
    public VersionResult update(long areaId,UpdateRequest request,HttpServletRequest http) {
        AreaRow area=area(areaId,true);
        String reason=required(request.changeReason(),"changeReason");
        Integer level=request.areaLevel()==null?1:request.areaLevel();
        if(level<1||level>9) throw new BizException("PARAM.INVALID","areaLevel must be between 1 and 9");
        List<Long> driverIds = request.driverIds();
        if ((driverIds == null || driverIds.isEmpty()) && request.primaryDriverId() != null) {
            driverIds = List.of(request.primaryDriverId());
        }
        if (driverIds == null || driverIds.isEmpty()) {
            throw new BizException("AREA.DRIVER.REQUIRED", "修改区域必须指定至少一名责任司机");
        }

        Map<String,Object> before=jdbc.queryForMap("SELECT area_name,area_level,status FROM delivery_area WHERE id=?",areaId);
        if(!"ACTIVE".equals(before.get("status"))) state("Inactive delivery area must be reactivated before editing");
        String normalized = DeliveryAreaGeoJson.normalize(mapper, request.geoJson());
        jdbc.update("UPDATE delivery_area SET area_name=?,area_level=?,boundary=ST_GeomFromGeoJSON(?,1,4326),geojson_snapshot=CAST(? AS JSON),version=version+1 WHERE id=?",
                required(request.areaName(),"areaName"),level,normalized,normalized,areaId);

        jdbc.update("UPDATE driver_area_preference SET status='INACTIVE' WHERE delivery_area_id=? AND status='ACTIVE'", areaId);
        for (int i = 0; i < driverIds.size(); i++) {
            Long driverId = driverIds.get(i);
            jdbc.update("""
                    INSERT INTO driver_area_preference(driver_id,delivery_area_id,priority,status,created_by)
                    VALUES (?, ?, ?, 'ACTIVE', ?)
                    ON DUPLICATE KEY UPDATE priority=VALUES(priority),status='ACTIVE',created_by=VALUES(created_by)
                    """, driverId, areaId, i + 1, operator(http));
        }

        audit(http,area.stationId(),"DELIVERY_AREA_UPDATED",areaId,"SUCCESS",reason,before,
                Map.of("areaName",request.areaName(),"areaLevel",level,"areaId",areaId,"driverIds",driverIds));
        return new VersionResult(areaId,areaId,1,"PUBLISHED");
    }

    @Transactional
    public Map<String,Object> deactivate(long areaId,StateChangeRequest request,HttpServletRequest http) {
        AreaRow area=area(areaId,true);
        String reason=required(request.reason(),"reason");
        String before=jdbc.queryForObject("SELECT status FROM delivery_area WHERE id=?",String.class,areaId);
        if("INACTIVE".equals(before)) return Map.of("areaId",areaId,"status","INACTIVE");
        jdbc.update("UPDATE delivery_area SET status='INACTIVE',version=version+1 WHERE id=?",areaId);
        jdbc.update("UPDATE driver_area_preference SET status='INACTIVE' WHERE delivery_area_id=? AND status='ACTIVE'",areaId);
        audit(http,area.stationId(),"DELIVERY_AREA_DEACTIVATED",areaId,"SUCCESS",reason,
                Map.of("status",before),Map.of("status","INACTIVE"));
        return Map.of("areaId",areaId,"status","INACTIVE");
    }

    @Transactional
    public Map<String,Object> activate(long areaId,StateChangeRequest request,HttpServletRequest http) {
        AreaRow area=area(areaId,true);
        String reason=required(request.reason(),"reason");
        String before=jdbc.queryForObject("SELECT status FROM delivery_area WHERE id=?",String.class,areaId);
        if("ACTIVE".equals(before)) return Map.of("areaId",areaId,"status","ACTIVE");
        jdbc.update("UPDATE delivery_area SET status='ACTIVE',version=version+1 WHERE id=?",areaId);
        audit(http,area.stationId(),"DELIVERY_AREA_REACTIVATED",areaId,"SUCCESS",reason,
                Map.of("status",before),Map.of("status","ACTIVE"));
        return Map.of("areaId",areaId,"status","ACTIVE");
    }

    @Transactional
    public ValidationResult validate(long areaId,long versionId,HttpServletRequest http) {
        AreaRow area=area(areaId,true);
        Boolean valid=jdbc.queryForObject("SELECT ST_IsValid(boundary) FROM delivery_area WHERE id=?",Boolean.class,areaId);
        if(!Boolean.TRUE.equals(valid)) throw new BizException("AREA.GEOMETRY.INVALID","Delivery area geometry is invalid");
        audit(http,area.stationId(),"DELIVERY_AREA_VALIDATED",areaId,"SUCCESS","Geometry validation",null,Map.of("areaId",areaId));
        return new ValidationResult(areaId,true,0);
    }

    @Transactional
    public VersionResult publish(long areaId,long versionId,PublishRequest request,HttpServletRequest http) {
        AreaRow area=area(areaId,true);
        audit(http,area.stationId(),"DELIVERY_AREA_PUBLISHED",areaId,"SUCCESS",required(request.reason(),"reason"),
                null,Map.of("areaId",areaId));
        return new VersionResult(areaId,areaId,1,"PUBLISHED");
    }

    private AreaRow area(long id,boolean lock){List<AreaRow> rows=jdbc.query("SELECT id,station_id FROM delivery_area WHERE id=?"+(lock?" FOR UPDATE":""),(rs,n)->new AreaRow(rs.getLong(1),rs.getLong(2)),id);if(rows.isEmpty())throw new BizException("AREA.NOT.FOUND","Delivery area not found: "+id);access.requireStation(rows.get(0).stationId());return rows.get(0);}
    private Long requireStation(){Long id=access.selectedStationId();if(id==null)throw new BizException("STATION.CONTEXT.REQUIRED","Station context is required");return id;}
    private Long operator(HttpServletRequest request){return request.getAttribute("operatorUserId") instanceof Long id?id:null;}
    private String required(String value,String field){if(value==null||value.isBlank())throw new BizException("PARAM.INVALID",field+" is required");return value.trim();}
    private void state(String message){throw new BizException("AREA.STATE.INVALID",message);}
    private void audit(HttpServletRequest request,long stationId,String action,long areaId,String outcome,String reason,Object before,Object after){jdbc.update("""
            INSERT INTO operation_audit_log(operator_user_id,actor_type,actor_id,station_id,action_code,resource_type,resource_id,outcome,reason_text,before_json,after_json,request_id,occurred_at)
            VALUES (?,'OPERATOR',?,? ,?,'DELIVERY_AREA',?,?,?,CAST(? AS JSON),CAST(? AS JSON),?,CURRENT_TIMESTAMP(3))
            """,operator(request),operator(request),stationId,action,String.valueOf(areaId),outcome,reason,json(before),json(after),request.getHeader("X-Request-Id"));}
    private String json(Object value){try{return value==null?null:mapper.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException(ex);}}

    private record AreaRow(long id,long stationId){}
    private record ParcelRow(long id,long waybillId){}
    private record MatchRow(long areaId,String areaCode){}
    public record CreateRequest(String areaCode,String areaName,Integer areaLevel,Long primaryDriverId,List<Long> driverIds,JsonNode geoJson,String changeReason){}
    public record VersionRequest(JsonNode geoJson,String changeReason){}
    public record UpdateRequest(String areaName,Integer areaLevel,Long primaryDriverId,List<Long> driverIds,JsonNode geoJson,String changeReason){}
    public record StateChangeRequest(String reason){}
    public record PublishRequest(String reason){}
    public record VersionResult(long areaId,long versionId,int versionNo,String status){}
    public record ValidationResult(long versionId,boolean valid,int overlapCount){}
    public record DriverPreferenceRequest(long driverId,Integer priority,java.time.LocalDate effectiveFrom,
                                          java.time.LocalDate effectiveTo,String reason){}
    public record ParcelLocationRequest(double longitude,double latitude,String providerCode,String precisionCode,
                                        java.math.BigDecimal confidence,String normalizedAddress,String reason){}
    public record ParcelAreaOverrideRequest(long areaVersionId,String reason){}
    public record AreaRecomputeRequest(List<Long> parcelIds,String reason){}
    public record AreaMatchResult(long parcelId,long areaId,long areaVersionId,String areaCode,int versionNo,String source){}
}
