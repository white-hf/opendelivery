package com.hf.easydelivery.operations.arrival.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

/**
 * Handling unit (pallet/cage/bag) entity. References its trip by id only (ADR rule),
 * so a unit row cannot be moved between trips through this mapping.
 */
@Entity
@Table(name = "handling_unit")
public class HandlingUnitEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private long tripId;

    @Column(name = "station_id", nullable = false)
    private long stationId;

    @Column(name = "external_unit_no", nullable = false, length = 100)
    private String externalUnitNo;

    @Column(name = "unit_type", nullable = false, length = 24)
    private String unitType;

    @Column(name = "expected_piece_count")
    private Integer expectedPieceCount;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "arrived_at")
    private LocalDateTime arrivedAt;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected HandlingUnitEntity() {
    }

    public HandlingUnitEntity(long tripId, long stationId, String externalUnitNo, String unitType,
                              Integer expectedPieceCount) {
        this.tripId = tripId;
        this.stationId = stationId;
        this.externalUnitNo = externalUnitNo;
        this.unitType = unitType;
        this.expectedPieceCount = expectedPieceCount;
        this.status = "EXPECTED";
    }

    public Long getId() {
        return id;
    }

    public long getTripId() {
        return tripId;
    }

    public long getStationId() {
        return stationId;
    }

    public String getExternalUnitNo() {
        return externalUnitNo;
    }

    public String getStatus() {
        return status;
    }

    public void moveTo(String targetStatus) {
        this.status = targetStatus;
        if ("ARRIVED".equals(targetStatus)) {
            this.arrivedAt = LocalDateTime.now();
        } else if ("OPENED".equals(targetStatus)) {
            this.openedAt = LocalDateTime.now();
        }
    }
}
