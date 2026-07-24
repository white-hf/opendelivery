package com.hf.easydelivery.operations.dayclose.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "driver_hold_approval")
public class DriverHoldApprovalEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parcel_id", nullable = false)
    private Long parcelId;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(name = "station_id", nullable = false)
    private Long stationId;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "reason_code", nullable = false, length = 64)
    private String reasonCode;

    @Column(name = "reason_text", length = 500)
    private String reasonText;

    @Column(name = "status", nullable = false, length = 24)
    private String status = "APPROVED";

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getParcelId() { return parcelId; }
    public void setParcelId(Long parcelId) { this.parcelId = parcelId; }
    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }
    public Long getStationId() { return stationId; }
    public void setStationId(Long stationId) { this.stationId = stationId; }
    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public String getReasonText() { return reasonText; }
    public void setReasonText(String reasonText) { this.reasonText = reasonText; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
