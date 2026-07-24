package com.hf.easydelivery.integration.ingestion.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "waybill")
public class WaybillEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "partner_id", nullable = false)
    private long partnerId;

    @Column(name = "external_waybill_no", nullable = false, length = 128)
    private String externalWaybillNo;

    @Column(name = "external_version", length = 64)
    private String externalVersion;

    @Column(name = "shipper_name", length = 160)
    private String shipperName;

    @Column(name = "recipient_name", nullable = false, length = 160)
    private String recipientName;

    @Column(name = "recipient_phone", length = 40)
    private String recipientPhone;

    @Column(name = "address_line1", nullable = false, length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "province", length = 100)
    private String province;

    @Column(name = "postal_code", nullable = false, length = 32)
    private String postalCode;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "service_code", length = 64)
    private String serviceCode;

    @Column(name = "delivery_window_start")
    private LocalDateTime deliveryWindowStart;

    @Column(name = "delivery_window_end")
    private LocalDateTime deliveryWindowEnd;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "routing_status", nullable = false, length = 24)
    private String routingStatus;

    @Column(name = "resolved_station_id")
    private Long resolvedStationId;

    @Column(name = "routing_reason_code", length = 64)
    private String routingReasonCode;

    @Column(name = "routed_at")
    private LocalDateTime routedAt;

    @Column(name = "source_event_time")
    private LocalDateTime sourceEventTime;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public long getPartnerId() { return partnerId; }
    public void setPartnerId(long partnerId) { this.partnerId = partnerId; }

    public String getExternalWaybillNo() { return externalWaybillNo; }
    public void setExternalWaybillNo(String externalWaybillNo) { this.externalWaybillNo = externalWaybillNo; }

    public String getExternalVersion() { return externalVersion; }
    public void setExternalVersion(String externalVersion) { this.externalVersion = externalVersion; }

    public String getShipperName() { return shipperName; }
    public void setShipperName(String shipperName) { this.shipperName = shipperName; }

    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }

    public String getRecipientPhone() { return recipientPhone; }
    public void setRecipientPhone(String recipientPhone) { this.recipientPhone = recipientPhone; }

    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }

    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }

    public LocalDateTime getDeliveryWindowStart() { return deliveryWindowStart; }
    public void setDeliveryWindowStart(LocalDateTime deliveryWindowStart) { this.deliveryWindowStart = deliveryWindowStart; }

    public LocalDateTime getDeliveryWindowEnd() { return deliveryWindowEnd; }
    public void setDeliveryWindowEnd(LocalDateTime deliveryWindowEnd) { this.deliveryWindowEnd = deliveryWindowEnd; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRoutingStatus() { return routingStatus; }
    public void setRoutingStatus(String routingStatus) { this.routingStatus = routingStatus; }

    public Long getResolvedStationId() { return resolvedStationId; }
    public void setResolvedStationId(Long resolvedStationId) { this.resolvedStationId = resolvedStationId; }

    public String getRoutingReasonCode() { return routingReasonCode; }
    public void setRoutingReasonCode(String routingReasonCode) { this.routingReasonCode = routingReasonCode; }

    public LocalDateTime getRoutedAt() { return routedAt; }
    public void setRoutedAt(LocalDateTime routedAt) { this.routedAt = routedAt; }

    public LocalDateTime getSourceEventTime() { return sourceEventTime; }
    public void setSourceEventTime(LocalDateTime sourceEventTime) { this.sourceEventTime = sourceEventTime; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
