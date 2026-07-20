ALTER TABLE station
    ADD COLUMN city VARCHAR(100) NULL AFTER station_name,
    ADD COLUMN province_code VARCHAR(32) NULL AFTER city,
    ADD COLUMN country_code CHAR(2) NULL AFTER province_code;

UPDATE station
SET city = 'HALIFAX', province_code = 'NS', country_code = 'CA'
WHERE city IS NULL;

ALTER TABLE station
    MODIFY city VARCHAR(100) NOT NULL,
    MODIFY province_code VARCHAR(32) NOT NULL,
    MODIFY country_code CHAR(2) NOT NULL,
    ADD UNIQUE KEY uk_station_city (country_code, province_code, city);

CREATE TABLE station_service_area (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    station_id BIGINT UNSIGNED NOT NULL,
    country_code CHAR(2) NOT NULL DEFAULT 'CA',
    province_code VARCHAR(32) NOT NULL,
    city_name VARCHAR(100) NOT NULL,
    postal_prefix VARCHAR(12) NULL,
    service_code VARCHAR(64) NULL,
    priority INT NOT NULL DEFAULT 100,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    effective_from DATETIME(3) NULL,
    effective_to DATETIME(3) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_area_match (country_code, province_code, city_name, status, postal_prefix),
    KEY idx_area_station_status (station_id, status),
    CONSTRAINT fk_area_station FOREIGN KEY (station_id) REFERENCES station (id),
    CONSTRAINT ck_area_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_area_dates CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to > effective_from)
) ENGINE=InnoDB;

ALTER TABLE waybill
    ADD COLUMN routing_status VARCHAR(24) NOT NULL DEFAULT 'PENDING' AFTER service_code,
    ADD COLUMN resolved_station_id BIGINT UNSIGNED NULL AFTER routing_status,
    ADD COLUMN routing_reason_code VARCHAR(64) NULL AFTER resolved_station_id,
    ADD COLUMN routed_at DATETIME(3) NULL AFTER routing_reason_code,
    ADD KEY idx_waybill_routing_queue (routing_status, created_at),
    ADD KEY idx_waybill_station_routing (resolved_station_id, routing_status, updated_at),
    ADD CONSTRAINT fk_waybill_resolved_station FOREIGN KEY (resolved_station_id) REFERENCES station (id),
    ADD CONSTRAINT ck_waybill_routing_status CHECK (routing_status IN ('PENDING', 'ROUTED', 'UNROUTABLE', 'AMBIGUOUS', 'OVERRIDDEN'));

UPDATE waybill w
JOIN (SELECT waybill_id, MIN(current_station_id) station_id
      FROM parcel WHERE current_station_id IS NOT NULL GROUP BY waybill_id) p ON p.waybill_id = w.id
SET w.routing_status = 'ROUTED', w.resolved_station_id = p.station_id,
    w.routing_reason_code = 'LEGACY_STATION', w.routed_at = CURRENT_TIMESTAMP(3);

