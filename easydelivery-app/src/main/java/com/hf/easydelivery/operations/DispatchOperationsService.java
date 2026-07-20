package com.hf.easydelivery.operations;

import com.hf.easydelivery.common.exception.BizException;
import com.hf.easydelivery.common.store.DeliveryOperations;
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
import java.util.List;
import java.util.Map;

@Service
@Profile("!memory")
public class DispatchOperationsService {
    private final JdbcTemplate jdbc;
    private final OperationsAccess access;
    private final DeliveryOperations deliveryOperations;

    public DispatchOperationsService(JdbcTemplate jdbc, OperationsAccess access, DeliveryOperations deliveryOperations) {
        this.jdbc=jdbc; this.access=access; this.deliveryOperations=deliveryOperations;
    }

    public List<Map<String,Object>> candidates(int limit,long afterId) {
        Long stationId=requireStationContext();
        return jdbc.queryForList("""
                SELECT p.id,p.tracking_no,p.status,p.route_code,p.promised_date,w.external_waybill_no,
                       w.recipient_name,w.address_line1,w.city,w.postal_code
                FROM parcel p JOIN waybill w ON w.id=p.waybill_id
                WHERE p.current_station_id=? AND p.id>? AND p.current_custody_type='STATION'
                  AND p.status IN ('AT_STATION','SORTED','READY_FOR_DISPATCH')
                  AND w.routing_status IN ('ROUTED','OVERRIDDEN') AND w.resolved_station_id=?
                  AND NOT EXISTS (SELECT 1 FROM operational_case c WHERE c.parcel_id=p.id
                                  AND c.status NOT IN ('RESOLVED','CLOSED'))
                ORDER BY p.id LIMIT ?
                """,stationId,afterId,stationId,Math.min(Math.max(limit,1),200));
    }

    public List<Map<String,Object>> activeDrivers() {
        Long stationId=requireStationContext();
        return jdbc.queryForList("""
                SELECT id,credential_id AS driver_code,driver_name AS display_name,status
                FROM driver WHERE home_station_id=? AND status='ACTIVE'
                ORDER BY display_name,id
                """,stationId);
    }

    public List<Map<String,Object>> waves(int limit,long afterId) {
        Long stationId=requireStationContext();
        return jdbc.queryForList("""
                SELECT w.id,w.wave_code,w.service_date,w.route_code,w.status,w.published_at,
                       t.id task_id,t.task_code,t.driver_id,t.status task_status,COUNT(ti.id) parcel_count
                FROM dispatch_wave w JOIN driver_task t ON t.wave_id=w.id
                LEFT JOIN driver_task_item ti ON ti.task_id=t.id
                WHERE w.station_id=? AND w.id>?
                GROUP BY w.id,w.wave_code,w.service_date,w.route_code,w.status,w.published_at,
                         t.id,t.task_code,t.driver_id,t.status
                ORDER BY w.id LIMIT ?
                """,stationId,afterId,Math.min(Math.max(limit,1),200));
    }

    @Transactional
    public DraftResult createDraft(DraftRequest request) {
        Long stationId=requireStationContext();
        Integer driver=jdbc.queryForObject("SELECT COUNT(*) FROM driver WHERE id=? AND home_station_id=? AND status='ACTIVE'",Integer.class,request.driverId(),stationId);
        if(driver==null||driver==0) throw new BizException("DRIVER.NOT.AVAILABLE","Driver is not active at selected station");
        GeneratedKeyHolder waveKeys=new GeneratedKeyHolder();
        jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO dispatch_wave(station_id,wave_code,service_date,route_code,status) VALUES (?,?,?,?,'DRAFT')",Statement.RETURN_GENERATED_KEYS);ps.setLong(1,stationId);ps.setString(2,request.waveCode());ps.setObject(3,request.serviceDate());ps.setString(4,request.routeCode());return ps;},waveKeys);
        long waveId=waveKeys.getKey().longValue();
        GeneratedKeyHolder taskKeys=new GeneratedKeyHolder();
        jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO driver_task(wave_id,driver_id,station_id,task_code,service_date,status) VALUES (?,?,?,?,?,'DRAFT')",Statement.RETURN_GENERATED_KEYS);ps.setLong(1,waveId);ps.setLong(2,request.driverId());ps.setLong(3,stationId);ps.setString(4,request.waveCode()+"-D"+request.driverId());ps.setObject(5,request.serviceDate());return ps;},taskKeys);
        long taskId=taskKeys.getKey().longValue();
        int sequence=1;
        try {
            for(String tracking:request.trackingNumbers()) {
                List<Long> parcels=jdbc.query("""
                        SELECT p.id FROM parcel p JOIN waybill w ON w.id=p.waybill_id
                        WHERE p.tracking_no=? AND p.current_station_id=? AND p.current_custody_type='STATION'
                          AND p.status IN ('AT_STATION','SORTED','READY_FOR_DISPATCH')
                          AND w.routing_status IN ('ROUTED','OVERRIDDEN') AND w.resolved_station_id=?
                          AND NOT EXISTS (SELECT 1 FROM operational_case c WHERE c.parcel_id=p.id AND c.status NOT IN ('RESOLVED','CLOSED'))
                        FOR UPDATE
                        """,(rs,n)->rs.getLong(1),tracking,stationId,stationId);
                if(parcels.isEmpty()) throw new BizException("PARCEL.NOT.DISPATCHABLE","Parcel is not dispatchable: "+tracking);
                jdbc.update("INSERT INTO driver_task_item(task_id,parcel_id,stop_sequence,item_status) VALUES (?,?,?,'ASSIGNED')",taskId,parcels.get(0),sequence++);
            }
        } catch(DataIntegrityViolationException ex) {
            throw new BizException("PARCEL.ACTIVE.TASK.EXISTS","A parcel already belongs to an active task");
        }
        return new DraftResult(waveId,taskId,request.trackingNumbers().size(),"DRAFT");
    }

    @Transactional
    public DraftResult publish(long waveId,HttpServletRequest request) {
        WaveRow wave=wave(waveId,true); access.requireStation(wave.stationId());
        if(!"DRAFT".equals(wave.status())) throw new BizException("WAVE.STATE.INVALID","Only a draft wave can be published");
        List<ParcelRow> parcels=jdbc.query("""
                SELECT p.id,p.status,p.current_station_id,p.current_custody_type
                FROM driver_task_item ti JOIN driver_task t ON t.id=ti.task_id JOIN parcel p ON p.id=ti.parcel_id
                WHERE t.wave_id=? FOR UPDATE
                """,(rs,n)->new ParcelRow(rs.getLong(1),rs.getString(2),rs.getLong(3),rs.getString(4)),waveId);
        if(parcels.isEmpty()) throw new BizException("WAVE.EMPTY","Wave has no parcels");
        for(ParcelRow p:parcels) if(p.stationId()!=wave.stationId()||!"STATION".equals(p.custody())||!List.of("AT_STATION","SORTED","READY_FOR_DISPATCH").contains(p.status())) throw new BizException("PARCEL.NOT.DISPATCHABLE","Wave contains stale inventory: "+p.id());
        Long operator=request.getAttribute("operatorUserId") instanceof Long id?id:null;
        jdbc.update("UPDATE dispatch_wave SET status='PUBLISHED',published_at=CURRENT_TIMESTAMP(3),published_by=?,version=version+1 WHERE id=?",operator,waveId);
        jdbc.update("UPDATE driver_task SET status='PUBLISHED',version=version+1 WHERE wave_id=?",waveId);
        for(ParcelRow p:parcels){jdbc.update("UPDATE parcel SET status='ASSIGNED',version=version+1 WHERE id=?",p.id());appendEvent(p.id(),p.status(),"ASSIGNED","TASK_ASSIGNED","wave-publish-"+waveId+"-"+p.id());}
        return new DraftResult(waveId,wave.taskId(),parcels.size(),"PUBLISHED");
    }

    @Transactional
    public void revoke(long waveId) {
        WaveRow wave=wave(waveId,true);access.requireStation(wave.stationId());
        Integer loaded=jdbc.queryForObject("SELECT COUNT(*) FROM driver_task_item ti JOIN driver_task t ON t.id=ti.task_id WHERE t.wave_id=? AND ti.item_status<>'ASSIGNED'",Integer.class,waveId);
        if(!"PUBLISHED".equals(wave.status())||loaded!=null&&loaded>0) throw new BizException("WAVE.REVOKE.BLOCKED","Only an unscanned published wave can be revoked");
        List<Long> parcels=jdbc.query("SELECT ti.parcel_id FROM driver_task_item ti JOIN driver_task t ON t.id=ti.task_id WHERE t.wave_id=?",(rs,n)->rs.getLong(1),waveId);
        jdbc.update("UPDATE driver_task_item ti JOIN driver_task t ON t.id=ti.task_id SET ti.item_status='CANCELLED' WHERE t.wave_id=?",waveId);
        jdbc.update("UPDATE driver_task SET status='CANCELLED',version=version+1 WHERE wave_id=?",waveId);
        jdbc.update("UPDATE dispatch_wave SET status='CANCELLED',version=version+1 WHERE id=?",waveId);
        for(Long id:parcels){jdbc.update("UPDATE parcel SET status='READY_FOR_DISPATCH',version=version+1 WHERE id=? AND status='ASSIGNED'",id);appendEvent(id,"ASSIGNED","READY_FOR_DISPATCH","WAVE_REVOKED","wave-revoke-"+waveId+"-"+id);}
    }

    @Transactional
    public DeliveryOperations.ScanBatch approveLoad(long sessionId,HttpServletRequest request) {
        List<Long> stations=jdbc.query("SELECT t.station_id FROM scan_session s JOIN driver_task t ON t.id=s.task_id WHERE s.id=? AND s.status='SUBMITTED' FOR UPDATE",(rs,n)->rs.getLong(1),sessionId);
        if(stations.isEmpty()) throw new BizException("SCAN.SESSION.NOT.SUBMITTED","Load session is not awaiting review");
        access.requireStation(stations.get(0));
        Long reviewer=request.getAttribute("operatorUserId") instanceof Long id?id:null;
        jdbc.update("UPDATE scan_session SET reviewed_by=? WHERE id=?",reviewer,sessionId);
        return deliveryOperations.reviewBatch(sessionId,"APPROVED");
    }

    private WaveRow wave(long id,boolean lock){List<WaveRow> rows=jdbc.query("SELECT w.id,w.station_id,w.status,t.id task_id FROM dispatch_wave w JOIN driver_task t ON t.wave_id=w.id WHERE w.id=?"+(lock?" FOR UPDATE":""),(rs,n)->new WaveRow(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getLong(4)),id);if(rows.isEmpty())throw new BizException("WAVE.NOT.FOUND","Wave not found: "+id);return rows.get(0);}
    private Long requireStationContext(){Long id=access.selectedStationId();if(id==null)throw new BizException("STATION.CONTEXT.REQUIRED","Station context is required");return id;}
    private void appendEvent(long parcelId,String from,String to,String type,String key){Long seq=jdbc.queryForObject("SELECT COALESCE(MAX(sequence_no),0)+1 FROM parcel_status_event WHERE parcel_id=?",Long.class,parcelId);jdbc.update("INSERT INTO parcel_status_event(parcel_id,sequence_no,from_status,to_status,event_type,idempotency_key,actor_type,occurred_at) VALUES (?,?,?,?,?,?,'OPERATOR',CURRENT_TIMESTAMP(3))",parcelId,seq,from,to,type,key);Long partner=jdbc.queryForObject("SELECT w.partner_id FROM parcel p JOIN waybill w ON w.id=p.waybill_id WHERE p.id=?",Long.class,parcelId);jdbc.update("INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,event_key,partner_id,payload_json) VALUES ('PARCEL',?,?,?,?,JSON_OBJECT('parcelId',?,'fromStatus',?,'toStatus',?))",parcelId,type,key,partner,parcelId,from,to);}

    private record WaveRow(long id,long stationId,String status,long taskId){}
    private record ParcelRow(long id,String status,long stationId,String custody){}
    public record DraftRequest(String waveCode, LocalDate serviceDate,String routeCode,long driverId,List<String> trackingNumbers){}
    public record DraftResult(long waveId,long taskId,int parcelCount,String status){}
}
