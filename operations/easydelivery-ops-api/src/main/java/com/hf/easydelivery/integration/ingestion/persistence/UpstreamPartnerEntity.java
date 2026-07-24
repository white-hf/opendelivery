package com.hf.easydelivery.integration.ingestion.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "upstream_partner")
public class UpstreamPartnerEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "partner_code", nullable = false, length = 64)
    private String partnerCode;

    @Column(name = "partner_name", nullable = false, length = 160)
    private String partnerName;

    @Column(name = "integration_mode", nullable = false, length = 16)
    private String integrationMode;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Column(name = "config_json", columnDefinition = "json")
    private String configJson;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPartnerCode() { return partnerCode; }
    public void setPartnerCode(String partnerCode) { this.partnerCode = partnerCode; }

    public String getPartnerName() { return partnerName; }
    public void setPartnerName(String partnerName) { this.partnerName = partnerName; }

    public String getIntegrationMode() { return integrationMode; }
    public void setIntegrationMode(String integrationMode) { this.integrationMode = integrationMode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
