package com.hf.easydelivery.operations;

import com.hf.easydelivery.common.exception.BizException;
import com.hf.easydelivery.common.exception.UnauthorizedException;
import com.hf.easydelivery.config.OperationsAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Profile("!memory")
public class FailureReturnService {
    private final JdbcTemplate jdbc;
    private final OperationsAccess access;
    public FailureReturnService(JdbcTemplate jdbc,OperationsAccess access){this.jdbc=jdbc;this.access=access;}

    public List<Map<String,Object>> reasons(){return jdbc.queryForList("SELECT reason_code,reason_name,requires_photo,requires_note,next_action,max_attempts FROM delivery_failure_reason WHERE status='ACTIVE' ORDER BY reason_code");}

    @Transactional
    public AttemptResult attempt(long taskItemId,int driverId,AttemptRequest request){
        String key=request.idempotencyKey()==null||request.idempotencyKey().isBlank()?UUID.randomUUID().toString():request.idempotencyKey();
        List<AttemptResult> prior=jdbc.query("SELECT id,outcome,next_action FROM delivery_attempt WHERE driver_id=? AND idempotency_key=?",(rs,n)->new AttemptResult(rs.getLong(1),rs.getString(2),rs.getString(3),true),driverId,key);
        if(!prior.isEmpty())return prior.get(0);
        List<TaskPiece> pieces=jdbc.query("""
                SELECT ti.parcel_id,t.id task_id,t.station_id,p.status FROM driver_task_item ti
                JOIN driver_task t ON t.id=ti.task_id JOIN parcel p ON p.id=ti.parcel_id
                WHERE ti.id=? AND t.driver_id=? AND ti.item_status='OUT_FOR_DELIVERY' FOR UPDATE
                """,(rs,n)->new TaskPiece(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getString(4)),taskItemId,driverId);
        if(pieces.isEmpty())throw new BizException("DELIVERY.TASK.INVALID","Task item is not out for delivery by this driver");
        TaskPiece piece=pieces.get(0);String outcome=request.outcome()==null?"":request.outcome().toUpperCase();
        String reason=null,nextAction=null;int maxAttempts=Short.MAX_VALUE;
        if("FAILED".equals(outcome)){
            List<Reason> rules=jdbc.query("SELECT reason_code,requires_photo,requires_note,next_action,max_attempts FROM delivery_failure_reason WHERE reason_code=? AND status='ACTIVE'",(rs,n)->new Reason(rs.getString(1),rs.getBoolean(2),rs.getBoolean(3),rs.getString(4),rs.getInt(5)),request.reasonCode());
            if(rules.isEmpty())throw new BizException("DELIVERY.REASON.INVALID","Active failure reason is required");
            Reason rule=rules.get(0);reason=rule.code();nextAction=rule.nextAction();maxAttempts=rule.maxAttempts();
            if(rule.photo()&&!request.photoEvidence())throw new BizException("DELIVERY.EVIDENCE.REQUIRED","Photo evidence is required");
            if(rule.note()&&(request.note()==null||request.note().isBlank()))throw new BizException("DELIVERY.NOTE.REQUIRED","Failure note is required");
        } else if(!"DELIVERED".equals(outcome)) throw new BizException("DELIVERY.OUTCOME.INVALID","Outcome must be DELIVERED or FAILED");
        Integer attemptNo=jdbc.queryForObject("SELECT COUNT(*)+1 FROM delivery_attempt WHERE parcel_id=?",Integer.class,piece.parcelId());
        if("FAILED".equals(outcome)&&attemptNo!=null&&attemptNo>maxAttempts)throw new BizException("DELIVERY.ATTEMPT.LIMIT","Maximum attempts reached for this reason");
        GeneratedKeyHolder keys=new GeneratedKeyHolder();String finalReason=reason,finalNext=nextAction;
        jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO delivery_attempt(task_item_id,parcel_id,driver_id,attempt_no,outcome,failure_reason_code,failure_note,next_action,latitude,longitude,idempotency_key,attempted_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP(3))",Statement.RETURN_GENERATED_KEYS);ps.setLong(1,taskItemId);ps.setLong(2,piece.parcelId());ps.setInt(3,driverId);ps.setInt(4,attemptNo==null?1:attemptNo);ps.setString(5,outcome);ps.setString(6,finalReason);ps.setString(7,request.note());ps.setString(8,finalNext);ps.setObject(9,request.latitude());ps.setObject(10,request.longitude());ps.setString(11,key);return ps;},keys);
        String target="DELIVERED".equals(outcome)?"DELIVERED":"DELIVERY_FAILED";String item="DELIVERED".equals(outcome)?"DELIVERED":"FAILED";
        jdbc.update("UPDATE parcel SET status=?,version=version+1 WHERE id=?",target,piece.parcelId());jdbc.update("UPDATE driver_task_item SET item_status=? WHERE id=?",item,taskItemId);append(piece.parcelId(),"OUT_FOR_DELIVERY",target,outcome,key);
        if("ADDRESS_CASE".equals(nextAction))createAddressCase(piece);
        return new AttemptResult(keys.getKey().longValue(),outcome,nextAction,false);
    }

    public Map<String,Object> closeout(long taskId,int driverId){List<Map<String,Object>> rows=jdbc.queryForList("SELECT status,COUNT(*) count FROM driver_task_item ti JOIN driver_task t ON t.id=ti.task_id WHERE t.id=? AND t.driver_id=? GROUP BY status",taskId,driverId);if(rows.isEmpty())throw new UnauthorizedException("Driver task not found");return Map.of("taskId",taskId,"items",rows,"closed",rows.stream().noneMatch(r->List.of("ASSIGNED","LOADED","OUT_FOR_DELIVERY").contains(r.get("status"))));}

    @Transactional public long openReturn(long taskId,int driverId){Integer valid=jdbc.queryForObject("SELECT COUNT(*) FROM driver_task WHERE id=? AND driver_id=?",Integer.class,taskId,driverId);if(valid==null||valid==0)throw new UnauthorizedException("Driver task not found");List<Long> active=jdbc.query("SELECT id FROM scan_session WHERE task_id=? AND session_type='RETURN' AND status IN ('OPEN','SUBMITTED')",(rs,n)->rs.getLong(1),taskId);if(!active.isEmpty())return active.get(0);Integer expected=jdbc.queryForObject("SELECT COUNT(*) FROM driver_task_item WHERE task_id=? AND item_status='FAILED'",Integer.class,taskId);if(expected==null||expected==0)throw new BizException("RETURN.EMPTY","Task has no failed parcels to return");GeneratedKeyHolder k=new GeneratedKeyHolder();jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO scan_session(task_id,driver_id,session_type,expected_count) VALUES (?,?,'RETURN',?)",Statement.RETURN_GENERATED_KEYS);ps.setLong(1,taskId);ps.setInt(2,driverId);ps.setInt(3,expected);return ps;},k);return k.getKey().longValue();}

    @Transactional public ScanResult scanReturn(long sessionId,int driverId,ScanRequest req){List<Long> sessions=jdbc.query("SELECT task_id FROM scan_session WHERE id=? AND driver_id=? AND session_type='RETURN' AND status='OPEN' FOR UPDATE",(rs,n)->rs.getLong(1),sessionId,driverId);if(sessions.isEmpty())throw new UnauthorizedException("Return session is not owned by driver");List<String> prior=jdbc.query("SELECT result_code FROM scan_event WHERE device_event_id=?",(rs,n)->rs.getString(1),req.deviceEventId());if(!prior.isEmpty())return new ScanResult(prior.get(0),true);List<Long> parcels=jdbc.query("SELECT p.id FROM parcel p JOIN driver_task_item ti ON ti.parcel_id=p.id WHERE ti.task_id=? AND ti.item_status='FAILED' AND p.tracking_no=?",(rs,n)->rs.getLong(1),sessions.get(0),req.trackingNo());String result=parcels.isEmpty()?"WRONG_TASK":"EXPECTED";jdbc.update("INSERT INTO scan_event(session_id,parcel_id,tracking_no,device_event_id,result_code,scanned_at) VALUES (?,?,?,?,?,?)",sessionId,parcels.isEmpty()?null:parcels.get(0),req.trackingNo(),req.deviceEventId(),result,req.occurredAt()==null?LocalDateTime.now():req.occurredAt());jdbc.update("UPDATE scan_session SET scanned_count=scanned_count+1,discrepancy_count=discrepancy_count+? WHERE id=?","EXPECTED".equals(result)?0:1,sessionId);return new ScanResult(result,false);}

    @Transactional public void submitReturn(long sessionId,int driverId){int n=jdbc.update("UPDATE scan_session SET status='SUBMITTED',submitted_at=CURRENT_TIMESTAMP(3),version=version+1 WHERE id=? AND driver_id=? AND session_type='RETURN' AND status='OPEN'",sessionId,driverId);if(n==0)throw new UnauthorizedException("Return session cannot be submitted");}

    @Transactional public ReturnResult approveReturn(long sessionId,ReturnDecision decision,HttpServletRequest request){List<ReturnRow> rows=jdbc.query("SELECT s.task_id,t.station_id,s.driver_id FROM scan_session s JOIN driver_task t ON t.id=s.task_id WHERE s.id=? AND s.session_type='RETURN' AND s.status='SUBMITTED' FOR UPDATE",(rs,n)->new ReturnRow(rs.getLong(1),rs.getLong(2),rs.getLong(3)),sessionId);if(rows.isEmpty())throw new BizException("RETURN.NOT.SUBMITTED","Return session is not submitted");ReturnRow row=rows.get(0);access.requireStation(row.stationId());List<Long> parcels=jdbc.query("SELECT parcel_id FROM scan_event WHERE session_id=? AND result_code='EXPECTED' AND parcel_id IS NOT NULL",(rs,n)->rs.getLong(1),sessionId);String target="RETURN_UPSTREAM".equals(decision.action())?"RETURNED_TO_UPSTREAM":"READY_FOR_DISPATCH";String custody="RETURN_UPSTREAM".equals(decision.action())?"UPSTREAM":"STATION";for(Long p:parcels){jdbc.update("UPDATE parcel SET status=?,current_custody_type=?,current_custody_id=?,version=version+1 WHERE id=? AND status='DELIVERY_FAILED'",target,custody,"STATION".equals(custody)?row.stationId():null,p);jdbc.update("UPDATE driver_task_item SET item_status=? WHERE task_id=? AND parcel_id=?","RETURN_UPSTREAM".equals(decision.action())?"RETURNED":"REASSIGNED",row.taskId(),p);jdbc.update("INSERT INTO custody_event(parcel_id,from_type,from_id,to_type,to_id,reason_code,reference_type,reference_id,occurred_at) VALUES (?,'DRIVER',? ,?,?,?,'SCAN_SESSION',?,CURRENT_TIMESTAMP(3))",p,row.driverId(),custody,"STATION".equals(custody)?row.stationId():null,decision.action(),sessionId);append(p,"DELIVERY_FAILED",target,decision.action(),"return-"+sessionId+"-"+p);}Long reviewer=request.getAttribute("operatorUserId") instanceof Long id?id:null;jdbc.update("UPDATE scan_session SET status='APPROVED',reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP(3),resolution_code=?,resolution_note=?,version=version+1 WHERE id=?",reviewer,decision.action(),decision.reason(),sessionId);return new ReturnResult(parcels.size(),target);}

    private void createAddressCase(TaskPiece p){String no="ADDR-"+p.parcelId()+"-"+System.currentTimeMillis();jdbc.update("INSERT INTO operational_case(case_no,case_type,parcel_id,station_id,priority,status) VALUES (?,'ADDRESS_EXCEPTION',?,?,'HIGH','OPEN')",no,p.parcelId(),p.stationId());}
    private void append(long parcel,String from,String to,String type,String key){Long seq=jdbc.queryForObject("SELECT COALESCE(MAX(sequence_no),0)+1 FROM parcel_status_event WHERE parcel_id=?",Long.class,parcel);jdbc.update("INSERT INTO parcel_status_event(parcel_id,sequence_no,from_status,to_status,event_type,idempotency_key,actor_type,occurred_at) VALUES (?,?,?,?,?,?,'DRIVER',CURRENT_TIMESTAMP(3))",parcel,seq,from,to,type,key);Long partner=jdbc.queryForObject("SELECT w.partner_id FROM parcel p JOIN waybill w ON w.id=p.waybill_id WHERE p.id=?",Long.class,parcel);jdbc.update("INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,event_key,partner_id,payload_json) VALUES ('PARCEL',?,?,?,?,JSON_OBJECT('parcelId',?,'fromStatus',?,'toStatus',?))",parcel,type,key,partner,parcel,from,to);}

    private record Reason(String code,boolean photo,boolean note,String nextAction,int maxAttempts){} private record TaskPiece(long parcelId,long taskId,long stationId,String status){} private record ReturnRow(long taskId,long stationId,long driverId){}
    public record AttemptRequest(String outcome,String reasonCode,String note,boolean photoEvidence,Double latitude,Double longitude,String idempotencyKey){} public record AttemptResult(long attemptId,String outcome,String nextAction,boolean duplicate){}
    public record ScanRequest(String trackingNo,String deviceEventId,LocalDateTime occurredAt){} public record ScanResult(String result,boolean duplicate){} public record ReturnDecision(String action,String reason){} public record ReturnResult(int parcelCount,String status){}
}
