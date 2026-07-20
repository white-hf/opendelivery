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
                  ORDER BY (candidate.status='PUBLISHED') DESC,candidate.version_no DESC LIMIT 1
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
    public record CreateRequest(String areaCode,String areaName,Integer areaLevel,JsonNode geoJson,String changeReason){}
    public record VersionRequest(JsonNode geoJson,String changeReason){}
    public record PublishRequest(String reason){}
    public record VersionResult(long areaId,long versionId,int versionNo,String status){}
    public record ValidationResult(long versionId,boolean valid,int overlapCount){}
}
