package com.hf.easydelivery.operations.dispatch.persistence;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "dispatch_wave")
public class DispatchWaveEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "station_id", nullable = false)
    private Long stationId;

    @Column(name = "wave_code", nullable = false, length = 64)
    private String waveCode;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStationId() { return stationId; }
    public void setStationId(Long stationId) { this.stationId = stationId; }
    public String getWaveCode() { return waveCode; }
    public void setWaveCode(String waveCode) { this.waveCode = waveCode; }
    public LocalDate getServiceDate() { return serviceDate; }
    public void setServiceDate(LocalDate serviceDate) { this.serviceDate = serviceDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
