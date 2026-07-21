package com.hf.easydelivery.integration.ingestion.persistence;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "parcel")
public class ParcelIngestionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "waybill_id", nullable = false)
    private long waybillId;

    @Column(name = "tracking_no", nullable = false, length = 128)
    private String trackingNo;

    @Column(name = "piece_no", nullable = false)
    private int pieceNo;

    @Column(name = "piece_count", nullable = false)
    private int pieceCount;

    @Column(name = "current_station_id")
    private Long currentStationId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "current_custody_type", nullable = false, length = 24)
    private String currentCustodyType;

    @Column(name = "current_custody_id")
    private Long currentCustodyId;

    @Column(name = "current_location_code", length = 128)
    private String currentLocationCode;

    @Column(name = "route_code", length = 64)
    private String routeCode;

    @Column(name = "promised_date")
    private LocalDate promisedDate;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public long getWaybillId() { return waybillId; }
    public void setWaybillId(long waybillId) { this.waybillId = waybillId; }

    public String getTrackingNo() { return trackingNo; }
    public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }

    public int getPieceNo() { return pieceNo; }
    public void setPieceNo(int pieceNo) { this.pieceNo = pieceNo; }

    public int getPieceCount() { return pieceCount; }
    public void setPieceCount(int pieceCount) { this.pieceCount = pieceCount; }

    public Long getCurrentStationId() { return currentStationId; }
    public void setCurrentStationId(Long currentStationId) { this.currentStationId = currentStationId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCurrentCustodyType() { return currentCustodyType; }
    public void setCurrentCustodyType(String currentCustodyType) { this.currentCustodyType = currentCustodyType; }

    public Long getCurrentCustodyId() { return currentCustodyId; }
    public void setCurrentCustodyId(Long currentCustodyId) { this.currentCustodyId = currentCustodyId; }

    public String getCurrentLocationCode() { return currentLocationCode; }
    public void setCurrentLocationCode(String currentLocationCode) { this.currentLocationCode = currentLocationCode; }

    public String getRouteCode() { return routeCode; }
    public void setRouteCode(String routeCode) { this.routeCode = routeCode; }

    public LocalDate getPromisedDate() { return promisedDate; }
    public void setPromisedDate(LocalDate promisedDate) { this.promisedDate = promisedDate; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
