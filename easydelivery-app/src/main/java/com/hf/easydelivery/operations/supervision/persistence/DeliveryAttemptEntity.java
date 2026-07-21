package com.hf.easydelivery.operations.supervision.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_attempt")
public class DeliveryAttemptEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parcel_id", nullable = false)
    private Long parcelId;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "attempt_result", nullable = false, length = 24)
    private String attemptResult;

    @Column(name = "failure_reason_code", length = 64)
    private String failureReasonCode;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "attempted_at", nullable = false)
    private LocalDateTime attemptedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getParcelId() { return parcelId; }
    public void setParcelId(Long parcelId) { this.parcelId = parcelId; }
    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getAttemptResult() { return attemptResult; }
    public void setAttemptResult(String attemptResult) { this.attemptResult = attemptResult; }
    public String getFailureReasonCode() { return failureReasonCode; }
    public void setFailureReasonCode(String failureReasonCode) { this.failureReasonCode = failureReasonCode; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public LocalDateTime getAttemptedAt() { return attemptedAt; }
    public void setAttemptedAt(LocalDateTime attemptedAt) { this.attemptedAt = attemptedAt; }
}
