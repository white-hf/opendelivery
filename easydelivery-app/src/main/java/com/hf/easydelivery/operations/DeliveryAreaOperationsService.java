package com.hf.easydelivery.operations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hf.easydelivery.common.exception.BizException;
import com.hf.easydelivery.config.OperationsAccess;
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

    public DeliveryAreaOperationsService(JdbcTemplate jdbc, OperationsAccess access, ObjectMapper mapper) {
        this.jdbc=jdbc; this.access=access; this.mapper=mapper;
    }

    public List<Map<String,Object>> areas() {
        Long stationId=requireStation();
        return jdbc.queryForList("""
                SELECT a.id,a.area_code,a.area_name,a.area_level,a.status,
                       v.id version_id,v.version_no,v.status version_status,
                       ST_AsGeoJSON(v.boundary) geo_json,v.effective_from,v.effective_to
                FROM delivery_area a LEFT JOIN delivery_area_version v ON v.id=(
                  SELECT candidate.id FROM delivery_area_version candidate
                  WHERE candidate.delivery_area_id=a.id
                  ORDER BY candidate.version_no DESC LIMIT 1
                )
                WHERE a.station_id=? ORDER BY a.area_level,a.area_code
                """,stationId);
    }

    public List<Map<String,Object>> versions(long areaId) {
        AreaRow area=area(areaId,false);
        return jdbc.queryForList("""
                SELECT id,version_no,status,ST_AsGeoJSON(boundary) geo_json,validation_json,
                       change_reason,effective_from,effective_to,created_by,approved_by,approved_at,created_at
                FROM delivery_area_version WHERE delivery_area_id=? ORDER BY version_no DESC
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
                SELECT a.id,v.id,a.area_code,v.version_no
                FROM delivery_area a JOIN delivery_area_version v ON v.delivery_area_id=a.id AND v.status='PUBLISHED'
                WHERE a.station_id=? AND a.status='ACTIVE'
                  AND ST_Intersects(v.boundary,ST_SRID(POINT(?,?),4326))
                ORDER BY a.area_level DESC,a.area_code LIMIT 1
                """,(rs,n)->new MatchRow(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getInt(4)),stationId,request.longitude(),request.latitude());
        if(matches.isEmpty()) throw new BizException("AREA.MATCH.NOT.FOUND","No published delivery area contains the parcel location");
        MatchRow match=matches.get(0);
        jdbc.update("UPDATE parcel_area_assignment SET ended_at=CURRENT_TIMESTAMP(3) WHERE parcel_id=? AND ended_at IS NULL",parcelId);
        jdbc.update("""
                INSERT INTO parcel_area_assignment(parcel_id,delivery_area_version_id,assignment_source,assignment_reason,assigned_by)
                VALUES (?,?,'GEO_POLYGON',?,?)
                """,parcelId,match.versionId(),request.reason(),operator(http));
        audit(http,stationId,"PARCEL_AREA_MATCHED",match.areaId(),"SUCCESS",required(request.reason(),"reason"),null,
                Map.of("parcelId",parcelId,"areaVersionId",match.versionId()));
        return new AreaMatchResult(parcelId,match.areaId(),match.versionId(),match.areaCode(),match.versionNo(),"GEO_POLYGON");
    }

    @Transactional
    public AreaMatchResult overrideParcelArea(long parcelId,ParcelAreaOverrideRequest request,HttpServletRequest http) {
        Long stationId=requireStation();
        List<ParcelRow> parcels=jdbc.query("SELECT id,waybill_id FROM parcel WHERE id=? AND current_station_id=? FOR UPDATE",(rs,n)->new ParcelRow(rs.getLong(1),rs.getLong(2)),parcelId,stationId);
        if(parcels.isEmpty())throw new BizException("PARCEL.NOT.FOUND","Parcel not found at selected station");
        List<MatchRow> areas=jdbc.query("""
                SELECT a.id,av.id,a.area_code,av.version_no FROM delivery_area_version av JOIN delivery_area a ON a.id=av.delivery_area_id
                WHERE av.id=? AND a.station_id=? AND a.status='ACTIVE' AND av.status='PUBLISHED'
                """,(rs,n)->new MatchRow(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getInt(4)),request.areaVersionId(),stationId);
        if(areas.isEmpty())throw new BizException("AREA.NOT.AVAILABLE","Published area does not belong to selected station");
        String reason=required(request.reason(),"reason");MatchRow area=areas.get(0);
        jdbc.update("UPDATE parcel_area_assignment SET ended_at=CURRENT_TIMESTAMP(3) WHERE parcel_id=? AND ended_at IS NULL",parcelId);
        jdbc.update("INSERT INTO parcel_area_assignment(parcel_id,delivery_area_version_id,assignment_source,assignment_reason,assigned_by) VALUES (?,?,'MANUAL_OVERRIDE',?,?)",parcelId,area.versionId(),reason,operator(http));
        audit(http,stationId,"PARCEL_AREA_OVERRIDDEN",area.areaId(),"SUCCESS",reason,null,Map.of("parcelId",parcelId,"areaVersionId",area.versionId()));
        return new AreaMatchResult(parcelId,area.areaId(),area.versionId(),area.areaCode(),area.versionNo(),"MANUAL_OVERRIDE");
    }

    @Transactional
    public VersionResult create(CreateRequest request,HttpServletRequest http) {
        Long stationId=requireStation();
        String code=required(request.areaCode(),"areaCode").toUpperCase();
        jdbc.update("INSERT INTO delivery_area(station_id,area_code,area_name,area_level) VALUES (?,?,?,?)",
                stationId,code,required(request.areaName(),"areaName"),request.areaLevel()==null?1:request.areaLevel());
        long areaId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        long versionId=insertVersion(areaId,1,request.geoJson(),request.changeReason(),operator(http));
        audit(http,stationId,"DELIVERY_AREA_CREATED",areaId,"SUCCESS",request.changeReason(),null,
                Map.of("areaCode",code,"versionId",versionId));
        return new VersionResult(areaId,versionId,1,"DRAFT");
    }

    @Transactional
    public VersionResult createVersion(long areaId,VersionRequest request,HttpServletRequest http) {
        AreaRow area=area(areaId,true);
        Integer next=jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM delivery_area_version WHERE delivery_area_id=?",Integer.class,areaId);
        long versionId=insertVersion(areaId,next,request.geoJson(),request.changeReason(),operator(http));
        audit(http,area.stationId(),"DELIVERY_AREA_VERSION_CREATED",areaId,"SUCCESS",request.changeReason(),null,
                Map.of("versionId",versionId,"versionNo",next));
        return new VersionResult(areaId,versionId,next,"DRAFT");
    }

    @Transactional
    public VersionResult update(long areaId,UpdateRequest request,HttpServletRequest http) {
        AreaRow area=area(areaId,true);
        String reason=required(request.changeReason(),"changeReason");
        Integer level=request.areaLevel()==null?1:request.areaLevel();
        if(level<1||level>9) throw new BizException("PARAM.INVALID","areaLevel must be between 1 and 9");
        Map<String,Object> before=jdbc.queryForMap("SELECT area_name,area_level,status FROM delivery_area WHERE id=?",areaId);
        if(!"ACTIVE".equals(before.get("status"))) state("Inactive delivery area must be reactivated before editing");
        jdbc.update("UPDATE delivery_area SET area_name=?,area_level=?,version=version+1 WHERE id=?",
                required(request.areaName(),"areaName"),level,areaId);
        Integer next=jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM delivery_area_version WHERE delivery_area_id=?",Integer.class,areaId);
        long versionId=insertVersion(areaId,next,request.geoJson(),reason,operator(http));
        audit(http,area.stationId(),"DELIVERY_AREA_UPDATED",areaId,"SUCCESS",reason,before,
                Map.of("areaName",request.areaName(),"areaLevel",level,"versionId",versionId));
        return new VersionResult(areaId,versionId,next,"DRAFT");
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
        AreaRow area=area(areaId,true); VersionRow version=version(areaId,versionId,true);
        if(!List.of("DRAFT","VALIDATED").contains(version.status())) state("Only draft or validated versions can be validated");
        Boolean valid=jdbc.queryForObject("SELECT ST_IsValid(boundary) FROM delivery_area_version WHERE id=?",Boolean.class,versionId);
        Integer overlaps=jdbc.queryForObject("""
                SELECT COUNT(*) FROM delivery_area_version other
                JOIN delivery_area oa ON oa.id=other.delivery_area_id
                JOIN delivery_area current_area ON current_area.id=?
                WHERE other.status='PUBLISHED' AND oa.station_id=current_area.station_id
                  AND oa.area_level=current_area.area_level AND other.delivery_area_id<>current_area.id
                  AND ST_Intersects(other.boundary,(SELECT boundary FROM delivery_area_version WHERE id=?))
                  AND NOT ST_Touches(other.boundary,(SELECT boundary FROM delivery_area_version WHERE id=?))
                """,Integer.class,areaId,versionId,versionId);
        if(!Boolean.TRUE.equals(valid)) throw new BizException("AREA.GEOMETRY.INVALID","Delivery area geometry is invalid");
        if(overlaps!=null&&overlaps>0) throw new BizException("AREA.OVERLAP","Delivery area overlaps a published peer area");
        jdbc.update("UPDATE delivery_area_version SET status='VALIDATED',validation_json=JSON_OBJECT('valid',true,'overlapCount',0) WHERE id=?",versionId);
        audit(http,area.stationId(),"DELIVERY_AREA_VALIDATED",areaId,"SUCCESS","Geometry validation",null,Map.of("versionId",versionId));
        return new ValidationResult(versionId,true,0);
    }

    @Transactional
    public VersionResult publish(long areaId,long versionId,PublishRequest request,HttpServletRequest http) {
        AreaRow area=area(areaId,true); VersionRow target=version(areaId,versionId,true);
        if(!"VALIDATED".equals(target.status())) state("Only a validated area version can be published");
        Long operator=operator(http);
        jdbc.update("UPDATE delivery_area_version SET status='RETIRED',effective_to=CURRENT_TIMESTAMP(3) WHERE delivery_area_id=? AND status='PUBLISHED'",areaId);
        jdbc.update("""
                UPDATE delivery_area_version SET status='PUBLISHED',effective_from=CURRENT_TIMESTAMP(3),effective_to=NULL,
                  approved_by=?,approved_at=CURRENT_TIMESTAMP(3) WHERE id=?
                """,operator,versionId);
        audit(http,area.stationId(),"DELIVERY_AREA_PUBLISHED",areaId,"SUCCESS",required(request.reason(),"reason"),
                Map.of("previousVersionId",target.previousPublishedId()==null?"":target.previousPublishedId()),Map.of("versionId",versionId));
        return new VersionResult(areaId,versionId,target.versionNo(),"PUBLISHED");
    }

    private long insertVersion(long areaId,int versionNo,JsonNode geoJson,String reason,Long operator) {
        String normalized=DeliveryAreaGeoJson.normalize(mapper,geoJson);
        try {
            jdbc.update("""
                    INSERT INTO delivery_area_version(delivery_area_id,version_no,boundary,geojson_snapshot,change_reason,created_by)
                    VALUES (?,?,ST_GeomFromGeoJSON(?,1,4326),CAST(? AS JSON),?,?)
                    """,areaId,versionNo,normalized,normalized,required(reason,"changeReason"),operator);
            return jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        } catch(DataAccessException ex) {
            throw new BizException("AREA.GEOMETRY.INVALID","Delivery area geometry is not a valid WGS84 polygon");
        }
    }

    private AreaRow area(long id,boolean lock){List<AreaRow> rows=jdbc.query("SELECT id,station_id FROM delivery_area WHERE id=?"+(lock?" FOR UPDATE":""),(rs,n)->new AreaRow(rs.getLong(1),rs.getLong(2)),id);if(rows.isEmpty())throw new BizException("AREA.NOT.FOUND","Delivery area not found: "+id);access.requireStation(rows.get(0).stationId());return rows.get(0);}
    private VersionRow version(long areaId,long id,boolean lock){List<VersionRow> rows=jdbc.query("""
            SELECT v.id,v.version_no,v.status,(SELECT id FROM delivery_area_version p WHERE p.delivery_area_id=v.delivery_area_id AND p.status='PUBLISHED' LIMIT 1)
            FROM delivery_area_version v WHERE v.id=? AND v.delivery_area_id=?"""+(lock?" FOR UPDATE":""),(rs,n)->new VersionRow(rs.getLong(1),rs.getInt(2),rs.getString(3),rs.getObject(4,Long.class)),id,areaId);if(rows.isEmpty())throw new BizException("AREA.VERSION.NOT.FOUND","Delivery area version not found");return rows.get(0);}
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
    private record VersionRow(long id,int versionNo,String status,Long previousPublishedId){}
    private record ParcelRow(long id,long waybillId){}
    private record MatchRow(long areaId,long versionId,String areaCode,int versionNo){}
    public record CreateRequest(String areaCode,String areaName,Integer areaLevel,JsonNode geoJson,String changeReason){}
    public record VersionRequest(JsonNode geoJson,String changeReason){}
    public record UpdateRequest(String areaName,Integer areaLevel,JsonNode geoJson,String changeReason){}
    public record StateChangeRequest(String reason){}
    public record PublishRequest(String reason){}
    public record VersionResult(long areaId,long versionId,int versionNo,String status){}
    public record ValidationResult(long versionId,boolean valid,int overlapCount){}
    public record DriverPreferenceRequest(long driverId,Integer priority,java.time.LocalDate effectiveFrom,
                                          java.time.LocalDate effectiveTo,String reason){}
    public record ParcelLocationRequest(double longitude,double latitude,String providerCode,String precisionCode,
                                        java.math.BigDecimal confidence,String normalizedAddress,String reason){}
    public record ParcelAreaOverrideRequest(long areaVersionId,String reason){}
    public record AreaMatchResult(long parcelId,long areaId,long areaVersionId,String areaCode,int versionNo,String source){}
}
