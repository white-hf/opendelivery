package com.hf.easydelivery.operations.dayclose.persistence;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_reconciliation")
public class DailyReconciliationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "station_id", nullable = false)
    private Long stationId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "inbound_count", nullable = false)
    private Integer inboundCount = 0;

    @Column(name = "dispatched_count", nullable = false)
    private Integer dispatchedCount = 0;

    @Column(name = "driver_return_count", nullable = false)
    private Integer driverReturnCount = 0;

    @Column(name = "delivered_count", nullable = false)
    private Integer deliveredCount = 0;

    @Column(name = "variance_count", nullable = false)
    private Integer varianceCount = 0;

    @Column(name = "open_case_count", nullable = false)
    private Integer openCaseCount = 0;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "carryover_reason", length = 1000)
    private String carryoverReason;

    @Column(name = "signed_off_by")
    private Long signedOffBy;

    @Column(name = "signed_off_at")
    private LocalDateTime signedOffAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStationId() { return stationId; }
    public void setStationId(Long stationId) { this.stationId = stationId; }
    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }
    public Integer getInboundCount() { return inboundCount; }
    public void setInboundCount(Integer inboundCount) { this.inboundCount = inboundCount; }
    public Integer getDispatchedCount() { return dispatchedCount; }
    public void setDispatchedCount(Integer dispatchedCount) { this.dispatchedCount = dispatchedCount; }
    public Integer getDriverReturnCount() { return driverReturnCount; }
    public void setDriverReturnCount(Integer driverReturnCount) { this.driverReturnCount = driverReturnCount; }
    public Integer getDeliveredCount() { return deliveredCount; }
    public void setDeliveredCount(Integer deliveredCount) { this.deliveredCount = deliveredCount; }
    public Integer getVarianceCount() { return varianceCount; }
    public void setVarianceCount(Integer varianceCount) { this.varianceCount = varianceCount; }
    public Integer getOpenCaseCount() { return openCaseCount; }
    public void setOpenCaseCount(Integer openCaseCount) { this.openCaseCount = openCaseCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCarryoverReason() { return carryoverReason; }
    public void setCarryoverReason(String carryoverReason) { this.carryoverReason = carryoverReason; }
    public Long getSignedOffBy() { return signedOffBy; }
    public void setSignedOffBy(Long signedOffBy) { this.signedOffBy = signedOffBy; }
    public LocalDateTime getSignedOffAt() { return signedOffAt; }
    public void setSignedOffAt(LocalDateTime signedOffAt) { this.signedOffAt = signedOffAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
