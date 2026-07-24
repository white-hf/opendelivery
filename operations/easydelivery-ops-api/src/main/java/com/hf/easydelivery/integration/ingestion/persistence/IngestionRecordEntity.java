package com.hf.easydelivery.integration.ingestion.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ingestion_record")
public class IngestionRecordEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private long batchId;

    @Column(name = "partner_id", nullable = false)
    private long partnerId;

    @Column(name = "external_event_id", nullable = false, length = 160)
    private String externalEventId;

    @Column(name = "external_waybill_no", length = 128)
    private String externalWaybillNo;

    @Column(name = "payload_json", nullable = false, columnDefinition = "json")
    private String payloadJson;

    @Column(name = "payload_sha256", nullable = false, length = 64)
    private String payloadSha256;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public long getBatchId() { return batchId; }
    public void setBatchId(long batchId) { this.batchId = batchId; }

    public long getPartnerId() { return partnerId; }
    public void setPartnerId(long partnerId) { this.partnerId = partnerId; }

    public String getExternalEventId() { return externalEventId; }
    public void setExternalEventId(String externalEventId) { this.externalEventId = externalEventId; }

    public String getExternalWaybillNo() { return externalWaybillNo; }
    public void setExternalWaybillNo(String externalWaybillNo) { this.externalWaybillNo = externalWaybillNo; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public String getPayloadSha256() { return payloadSha256; }
    public void setPayloadSha256(String payloadSha256) { this.payloadSha256 = payloadSha256; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
