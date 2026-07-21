package com.hf.easydelivery.operations.arrival;

import com.hf.easydelivery.common.exception.BizException;
import com.hf.easydelivery.config.OperationsAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Profile("!memory")
public class InboundOperationsService {
    private final JdbcTemplate jdbc;
    private final OperationsAccess access;

    public InboundOperationsService(JdbcTemplate jdbc, OperationsAccess access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    public List<Map<String,Object>> manifests(String status, int limit, long afterId) {
        Long stationId = requireContext();
        return jdbc.queryForList("""
                SELECT m.id,m.external_manifest_no,m.status,m.expected_count,m.received_count,m.discrepancy_count,
                       m.expected_arrival_at,m.actual_arrival_at,m.version,p.partner_code
                FROM inbound_manifest m JOIN upstream_partner p ON p.id=m.partner_id
                WHERE m.station_id=? AND m.id>? AND (? IS NULL OR m.status=?)
                ORDER BY m.id LIMIT ?
                """, stationId, afterId, status, status, Math.min(Math.max(limit,1),200));
    }

    public ManifestDetail detail(long manifestId) {
        ManifestHeader header = manifest(manifestId, false);
        List<Map<String,Object>> items = jdbc.queryForList("""
                SELECT mi.id,mi.expected_tracking_no,mi.receipt_status,mi.received_at,mi.discrepancy_reason,
                       p.id parcel_id,p.status parcel_status
                FROM inbound_manifest_item mi LEFT JOIN parcel p ON p.id=mi.parcel_id
                WHERE mi.manifest_id=? ORDER BY mi.id
                """, manifestId);
        return new ManifestDetail(header, items);
    }

    @Transactional
    public ManifestHeader start(long manifestId) {
        ManifestHeader header = manifest(manifestId, true);
        if ("CLOSED".equals(header.status()) || "CANCELLED".equals(header.status())) {
            throw new BizException("MANIFEST.STATE.INVALID", "Closed or cancelled manifest cannot start receiving");
        }
        jdbc.update("""
                UPDATE inbound_manifest SET status='RECEIVING',actual_arrival_at=COALESCE(actual_arrival_at,CURRENT_TIMESTAMP(3)),
                  version=version+1 WHERE id=? AND status='EXPECTED'
                """, manifestId);
        return manifest(manifestId, false);
    }

    @Transactional
    public ScanResult scan(long manifestId, ScanRequest request, HttpServletRequest httpRequest) {
        ManifestHeader header = manifest(manifestId, true);
        if (!List.of("RECEIVING","DISCREPANCY").contains(header.status())) {
            throw new BizException("MANIFEST.STATE.INVALID", "Manifest must be receiving before scanning");
        }
        List<ScanResult> duplicate = jdbc.query("""
                SELECT outcome,manifest_item_id FROM inbound_scan_event WHERE manifest_id=? AND device_event_id=?
                """, (rs,n)->new ScanResult(rs.getString(1),rs.getObject(2,Long.class),true), manifestId, request.deviceEventId());
        if (!duplicate.isEmpty()) return duplicate.get(0);

        List<ItemRow> expected = jdbc.query("""
                SELECT mi.id,mi.parcel_id,mi.receipt_status,p.status,p.current_station_id
                FROM inbound_manifest_item mi LEFT JOIN parcel p ON p.id=mi.parcel_id
                WHERE mi.manifest_id=? AND mi.expected_tracking_no=? FOR UPDATE
                """, (rs,n)->new ItemRow(rs.getLong(1),rs.getObject(2,Long.class),rs.getString(3),
                rs.getString(4),rs.getObject(5,Long.class)), manifestId, request.trackingNo());
        String outcome;
        long itemId;
        Long parcelId;
        if (!expected.isEmpty()) {
            ItemRow item = expected.get(0);
            itemId=item.id(); parcelId=item.parcelId();
            if ("RECEIVED".equals(item.receiptStatus()) || "DAMAGED".equals(item.receiptStatus())) {
                outcome="DUPLICATE";
            } else if (item.stationId()!=null && item.stationId()!=header.stationId()) {
                outcome="WRONG_STATION";
                markDiscrepancy(manifestId,itemId,parcelId,outcome);
            } else if ("DAMAGED".equalsIgnoreCase(request.conditionCode())) {
                outcome="DAMAGED";
                jdbc.update("UPDATE inbound_manifest_item SET receipt_status='DAMAGED',received_at=CURRENT_TIMESTAMP(3),discrepancy_reason='DAMAGED' WHERE id=?",itemId);
                markParcelReceived(parcelId,header.stationId(),"DAMAGED",manifestId);
                createCase(manifestId,itemId,parcelId,header.stationId(),"INBOUND_DAMAGED");
            } else {
                outcome="RECEIVED";
                jdbc.update("UPDATE inbound_manifest_item SET receipt_status='RECEIVED',received_at=CURRENT_TIMESTAMP(3) WHERE id=?",itemId);
                markParcelReceived(parcelId,header.stationId(),"AT_STATION",manifestId);
            }
        } else {
            List<ParcelLookup> parcels=jdbc.query("SELECT id,current_station_id FROM parcel WHERE tracking_no=?",
                    (rs,n)->new ParcelLookup(rs.getLong(1),rs.getObject(2,Long.class)),request.trackingNo());
            parcelId=parcels.isEmpty()?null:parcels.get(0).id();
            outcome=!parcels.isEmpty() && parcels.get(0).stationId()!=null && parcels.get(0).stationId()!=header.stationId()
                    ? "WRONG_STATION":"EXTRA";
            jdbc.update("INSERT INTO inbound_manifest_item(manifest_id,parcel_id,expected_tracking_no,receipt_status,received_at,discrepancy_reason) VALUES (?,?,?,?,CURRENT_TIMESTAMP(3),?)",
                    manifestId,parcelId,request.trackingNo(),outcome,outcome);
            itemId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
            createCase(manifestId,itemId,parcelId,header.stationId(),"INBOUND_"+outcome);
        }
        Long operatorId=httpRequest.getAttribute("operatorUserId") instanceof Long id?id:null;
        jdbc.update("INSERT INTO inbound_scan_event(manifest_id,device_event_id,tracking_no,condition_code,outcome,manifest_item_id,operator_user_id,occurred_at) VALUES (?,?,?,?,?,?,?,?)",
                manifestId,request.deviceEventId(),request.trackingNo(),normalizedCondition(request.conditionCode()),outcome,itemId,operatorId,
                request.occurredAt()==null?LocalDateTime.now():request.occurredAt());
        recalculate(manifestId);
        return new ScanResult(outcome,itemId,false);
    }

    @Transactional
    public void resolveDiscrepancy(long manifestId,long itemId,DecisionRequest request,HttpServletRequest httpRequest) {
        ManifestHeader header=manifest(manifestId,true);
        List<String> statuses=jdbc.query("SELECT receipt_status FROM inbound_manifest_item WHERE id=? AND manifest_id=? FOR UPDATE",
                (rs,n)->rs.getString(1),itemId,manifestId);
        if(statuses.isEmpty()) throw new BizException("MANIFEST.ITEM.NOT.FOUND","Manifest item not found");
        String decision=InboundDiscrepancyPolicy.validate(statuses.get(0),request.decision(),request.reason());
        Long operatorId=httpRequest.getAttribute("operatorUserId") instanceof Long id?id:null;
        int changed=jdbc.update("""
                UPDATE operational_case SET status='RESOLVED',resolution_code=?,resolution_note=?,resolved_at=CURRENT_TIMESTAMP(3),version=version+1
                WHERE inbound_manifest_id=? AND manifest_item_id=? AND status NOT IN ('RESOLVED','CLOSED')
                """,decision,request.reason().trim(),manifestId,itemId);
        if(changed==0) throw new BizException("CASE.NOT.FOUND","Open discrepancy case not found");
        jdbc.update("""
                INSERT INTO case_action(case_id,action_type,to_status,actor_type,actor_id,note)
                SELECT id,'DISCREPANCY_DECISION','RESOLVED','OPERATOR',?,? FROM operational_case
                WHERE inbound_manifest_id=? AND manifest_item_id=?
                """,operatorId,request.reason().trim(),manifestId,itemId);
        recalculate(manifestId);
    }

    @Transactional
    public ManifestHeader close(long manifestId, CloseRequest request, HttpServletRequest httpRequest) {
        ManifestHeader header=manifest(manifestId,true);
        jdbc.update("UPDATE inbound_manifest_item SET receipt_status='MISSING',discrepancy_reason='MISSING' WHERE manifest_id=? AND receipt_status='EXPECTED'",manifestId);
        List<Long> missing=jdbc.query("SELECT id FROM inbound_manifest_item WHERE manifest_id=? AND receipt_status='MISSING'",
                (rs,n)->rs.getLong(1),manifestId);
        for(Long itemId:missing) createCaseIfAbsent(manifestId,itemId,header.stationId(),"INBOUND_MISSING");
        recalculate(manifestId);
        Integer open=jdbc.queryForObject("SELECT COUNT(*) FROM operational_case WHERE inbound_manifest_id=? AND status NOT IN ('RESOLVED','CLOSED')",Integer.class,manifestId);
        if(open!=null&&open>0&&!request.allowCaseCarryover()) {
            throw new BizException("MANIFEST.DISCREPANCY.OPEN","Open discrepancy cases must be resolved or explicitly carried over");
        }
        Long operatorId=httpRequest.getAttribute("operatorUserId") instanceof Long id?id:null;
        jdbc.update("UPDATE inbound_manifest SET status='CLOSED',closed_by=?,closed_at=CURRENT_TIMESTAMP(3),version=version+1 WHERE id=?",operatorId,manifestId);
        return manifest(manifestId,false);
    }

    private ManifestHeader manifest(long id,boolean lock) {
        List<ManifestHeader> rows=jdbc.query("SELECT id,station_id,external_manifest_no,status,expected_count,received_count,discrepancy_count,version FROM inbound_manifest WHERE id=?"+(lock?" FOR UPDATE":""),
                (rs,n)->new ManifestHeader(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getInt(5),rs.getInt(6),rs.getInt(7),rs.getLong(8)),id);
        if(rows.isEmpty()) throw new BizException("MANIFEST.NOT.FOUND","Manifest not found: "+id);
        access.requireStation(rows.get(0).stationId()); return rows.get(0);
    }
    private Long requireContext(){Long id=access.selectedStationId();if(id==null)throw new BizException("STATION.CONTEXT.REQUIRED","Station context is required");return id;}
    private String normalizedCondition(String value){return "DAMAGED".equalsIgnoreCase(value)?"DAMAGED":"NORMAL";}
    private void markParcelReceived(Long parcelId,long stationId,String status,long manifestId){
        if(parcelId==null)return;
        int changed=jdbc.update("UPDATE parcel SET status=?,current_station_id=?,current_custody_type='STATION',current_custody_id=?,current_location_code='RECEIVING',version=version+1 WHERE id=? AND status='RECEIVED'",status,stationId,stationId,parcelId);
        if(changed==0)return;
        jdbc.update("""
                INSERT INTO custody_event(parcel_id,from_type,to_type,to_id,reason_code,reference_type,reference_id,occurred_at)
                VALUES (?,'UPSTREAM','STATION',?,'INBOUND_RECEIPT','MANIFEST',?,CURRENT_TIMESTAMP(3))
                """,parcelId,stationId,manifestId);
        Long sequence=jdbc.queryForObject("SELECT COALESCE(MAX(sequence_no),0)+1 FROM parcel_status_event WHERE parcel_id=?",Long.class,parcelId);
        String eventType="DAMAGED".equals(status)?"INBOUND_DAMAGED":"INBOUND_RECEIPT";
        String eventKey="inbound-"+manifestId+"-"+parcelId;
        jdbc.update("""
                INSERT INTO parcel_status_event(parcel_id,sequence_no,from_status,to_status,event_type,idempotency_key,actor_type,occurred_at)
                VALUES (?,?,'RECEIVED',?,?,?,'OPERATOR',CURRENT_TIMESTAMP(3))
                """,parcelId,sequence,status,eventType,eventKey);
        Long partnerId=jdbc.queryForObject("SELECT w.partner_id FROM parcel p JOIN waybill w ON w.id=p.waybill_id WHERE p.id=?",Long.class,parcelId);
        jdbc.update("""
                INSERT INTO outbox_event(aggregate_type,aggregate_id,event_type,event_key,partner_id,payload_json)
                VALUES ('PARCEL',?,?,?,?,JSON_OBJECT('parcelId',?,'fromStatus','RECEIVED','toStatus',?))
                """,parcelId,eventType,eventKey,partnerId,parcelId,status);
    }
    private void markDiscrepancy(long manifestId,long itemId,Long parcelId,String reason){jdbc.update("UPDATE inbound_manifest_item SET receipt_status=?,received_at=CURRENT_TIMESTAMP(3),discrepancy_reason=? WHERE id=?",reason,reason,itemId);createCase(manifestId,itemId,parcelId,manifest(manifestId,false).stationId(),"INBOUND_"+reason);}
    private void createCase(long manifestId,long itemId,Long parcelId,long stationId,String type){String no="INB-"+manifestId+"-"+itemId;jdbc.update("INSERT INTO operational_case(case_no,case_type,parcel_id,inbound_manifest_id,manifest_item_id,station_id,priority,status) VALUES (?,?,?,?,?,?,'HIGH','OPEN') ON DUPLICATE KEY UPDATE updated_at=CURRENT_TIMESTAMP(3)",no,type,parcelId,manifestId,itemId,stationId);}
    private void createCaseIfAbsent(long manifestId,long itemId,long stationId,String type){createCase(manifestId,itemId,null,stationId,type);}
    private void recalculate(long id){jdbc.update("""
            UPDATE inbound_manifest SET received_count=(SELECT COUNT(*) FROM inbound_manifest_item WHERE manifest_id=? AND receipt_status IN ('RECEIVED','DAMAGED')),
              discrepancy_count=(SELECT COUNT(*) FROM inbound_manifest_item WHERE manifest_id=? AND receipt_status IN ('MISSING','EXTRA','WRONG_STATION','DAMAGED')),
              status=IF((SELECT COUNT(*) FROM inbound_manifest_item WHERE manifest_id=? AND receipt_status IN ('MISSING','EXTRA','WRONG_STATION','DAMAGED'))>0,'DISCREPANCY','RECEIVING'),version=version+1 WHERE id=?
            """,id,id,id,id);}

    private record ItemRow(long id,Long parcelId,String receiptStatus,String parcelStatus,Long stationId){}
    private record ParcelLookup(long id,Long stationId){}
    public record ManifestHeader(long id,long stationId,String manifestNo,String status,int expectedCount,int receivedCount,int discrepancyCount,long version){}
    public record ManifestDetail(ManifestHeader manifest,List<Map<String,Object>> items){}
    public record ScanRequest(String trackingNo,String deviceEventId,String conditionCode,LocalDateTime occurredAt){}
    public record ScanResult(String outcome,Long manifestItemId,boolean duplicate){}
    public record DecisionRequest(String decision,String reason){}
    public record CloseRequest(boolean allowCaseCarryover){}
}
