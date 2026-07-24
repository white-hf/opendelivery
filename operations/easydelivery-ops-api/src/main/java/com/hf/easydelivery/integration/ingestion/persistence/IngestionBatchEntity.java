package com.hf.easydelivery.integration.ingestion.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ingestion_batch")
public class IngestionBatchEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "partner_id", nullable = false)
    private long partnerId;

    @Column(name = "external_batch_no", length = 128)
    private String externalBatchNo;

    @Column(name = "source_type", nullable = false, length = 16)
    private String sourceType;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "received_count", nullable = false)
    private int receivedCount;

    @Column(name = "accepted_count", nullable = false)
    private int acceptedCount;

    @Column(name = "rejected_count", nullable = false)
    private int rejectedCount;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public long getPartnerId() { return partnerId; }
    public void setPartnerId(long partnerId) { this.partnerId = partnerId; }

    public String getExternalBatchNo() { return externalBatchNo; }
    public void setExternalBatchNo(String externalBatchNo) { this.externalBatchNo = externalBatchNo; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getReceivedCount() { return receivedCount; }
    public void setReceivedCount(int receivedCount) { this.receivedCount = receivedCount; }

    public int getAcceptedCount() { return acceptedCount; }
    public void setAcceptedCount(int acceptedCount) { this.acceptedCount = acceptedCount; }

    public int getRejectedCount() { return rejectedCount; }
    public void setRejectedCount(int rejectedCount) { this.rejectedCount = rejectedCount; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
