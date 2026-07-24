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
 * Arrival batch (truck trip) entity. No association navigation per the persistence ADR —
 * related rows are referenced by id. created_at/updated_at are database-managed.
 */
@Entity
@Table(name = "arrival_trip")
public class ArrivalTripEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "station_id", nullable = false)
    private long stationId;

    @Column(name = "external_trip_no", nullable = false, length = 100)
    private String externalTripNo;

    @Column(name = "upstream_partner_id")
    private Long upstreamPartnerId;

    @Column(name = "vehicle_plate", length = 40)
    private String vehiclePlate;

    @Column(name = "seal_no", length = 80)
    private String sealNo;

    @Column(name = "expected_at")
    private LocalDateTime expectedAt;

    @Column(name = "arrived_at")
    private LocalDateTime arrivedAt;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "note", length = 500)
    private String note;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected ArrivalTripEntity() {
    }

    public ArrivalTripEntity(long stationId, String externalTripNo, String vehiclePlate, String sealNo,
                             LocalDateTime expectedAt, String note) {
        this.stationId = stationId;
        this.externalTripNo = externalTripNo;
        this.vehiclePlate = vehiclePlate;
        this.sealNo = sealNo;
        this.expectedAt = expectedAt;
        this.note = note;
        this.status = "EXPECTED";
    }

    public Long getId() {
        return id;
    }

    public long getStationId() {
        return stationId;
    }

    public String getExternalTripNo() {
        return externalTripNo;
    }

    public String getStatus() {
        return status;
    }

    public void arrive() {
        this.status = "ARRIVED";
        this.arrivedAt = LocalDateTime.now();
    }

    public void moveTo(String targetStatus) {
        this.status = targetStatus;
    }
}
