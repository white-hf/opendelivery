CREATE TABLE delivery_area (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    station_id BIGINT UNSIGNED NOT NULL,
    area_code VARCHAR(80) NOT NULL,
    area_name VARCHAR(160) NOT NULL,
    parent_area_id BIGINT UNSIGNED NULL,
    area_level SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_delivery_area_station_code (station_id,area_code),
    KEY idx_delivery_area_station_status (station_id,status,area_level),
    CONSTRAINT fk_delivery_area_station FOREIGN KEY (station_id) REFERENCES station(id),
    CONSTRAINT fk_delivery_area_parent FOREIGN KEY (parent_area_id) REFERENCES delivery_area(id),
    CONSTRAINT ck_delivery_area_status CHECK (status IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB;

CREATE TABLE delivery_area_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    delivery_area_id BIGINT UNSIGNED NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    boundary MULTIPOLYGON NOT NULL SRID 4326,
    geojson_snapshot JSON NOT NULL,
    effective_from DATETIME(3) NULL,
    effective_to DATETIME(3) NULL,
    validation_json JSON NULL,
    change_reason VARCHAR(500) NOT NULL,
    created_by BIGINT UNSIGNED NULL,
    approved_by BIGINT UNSIGNED NULL,
    approved_at DATETIME(3) NULL,
    published_slot TINYINT GENERATED ALWAYS AS
      (CASE WHEN status='PUBLISHED' THEN 1 ELSE NULL END) STORED,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_delivery_area_version (delivery_area_id,version_no),
    UNIQUE KEY uk_delivery_area_published (delivery_area_id,published_slot),
    KEY idx_area_version_status_effective (status,effective_from,effective_to),
    SPATIAL INDEX idx_area_version_boundary (boundary),
    CONSTRAINT fk_area_version_area FOREIGN KEY (delivery_area_id) REFERENCES delivery_area(id),
    CONSTRAINT fk_area_version_creator FOREIGN KEY (created_by) REFERENCES operator_user(id),
    CONSTRAINT fk_area_version_approver FOREIGN KEY (approved_by) REFERENCES operator_user(id),
    CONSTRAINT ck_area_version_status CHECK (status IN ('DRAFT','VALIDATED','PUBLISHED','RETIRED')),
    CONSTRAINT ck_area_version_effective CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to>effective_from)
) ENGINE=InnoDB;

CREATE TABLE driver_area_preference (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    driver_id BIGINT UNSIGNED NOT NULL,
    delivery_area_id BIGINT UNSIGNED NOT NULL,
    priority SMALLINT UNSIGNED NOT NULL DEFAULT 100,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    effective_from DATE NULL,
    effective_to DATE NULL,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_driver_area_preference (driver_id,delivery_area_id),
    KEY idx_area_preferred_drivers (delivery_area_id,status,priority),
    CONSTRAINT fk_preference_driver FOREIGN KEY (driver_id) REFERENCES driver(id),
    CONSTRAINT fk_preference_area FOREIGN KEY (delivery_area_id) REFERENCES delivery_area(id),
    CONSTRAINT fk_preference_creator FOREIGN KEY (created_by) REFERENCES operator_user(id),
    CONSTRAINT ck_preference_status CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT ck_preference_effective CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to>=effective_from)
) ENGINE=InnoDB;

CREATE TABLE waybill_geocode (
    waybill_id BIGINT UNSIGNED NOT NULL,
    delivery_point POINT NOT NULL SRID 4326,
    provider_code VARCHAR(64) NOT NULL,
    precision_code VARCHAR(32) NOT NULL,
    confidence DECIMAL(5,4) NULL,
    normalized_address VARCHAR(500) NULL,
    geocoded_at DATETIME(3) NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (waybill_id),
    SPATIAL INDEX idx_waybill_delivery_point (delivery_point),
    CONSTRAINT fk_geocode_waybill FOREIGN KEY (waybill_id) REFERENCES waybill(id),
    CONSTRAINT ck_geocode_confidence CHECK (confidence IS NULL OR (confidence>=0 AND confidence<=1))
) ENGINE=InnoDB;

CREATE TABLE parcel_area_assignment (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    parcel_id BIGINT UNSIGNED NOT NULL,
    delivery_area_version_id BIGINT UNSIGNED NOT NULL,
    assignment_source VARCHAR(24) NOT NULL,
    assignment_reason VARCHAR(500) NULL,
    assigned_by BIGINT UNSIGNED NULL,
    assigned_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ended_at DATETIME(3) NULL,
    active_slot TINYINT GENERATED ALWAYS AS
      (CASE WHEN ended_at IS NULL THEN 1 ELSE NULL END) STORED,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_parcel_current_area (parcel_id,active_slot),
    KEY idx_area_assignment_version (delivery_area_version_id,ended_at,parcel_id),
    CONSTRAINT fk_assignment_parcel FOREIGN KEY (parcel_id) REFERENCES parcel(id),
    CONSTRAINT fk_assignment_area_version FOREIGN KEY (delivery_area_version_id) REFERENCES delivery_area_version(id),
    CONSTRAINT fk_assignment_operator FOREIGN KEY (assigned_by) REFERENCES operator_user(id),
    CONSTRAINT ck_assignment_source CHECK (assignment_source IN ('GEO_POLYGON','POSTAL_FALLBACK','MANUAL_OVERRIDE'))
) ENGINE=InnoDB;

ALTER TABLE operation_audit_log
    ADD COLUMN actor_type VARCHAR(24) NOT NULL DEFAULT 'OPERATOR' AFTER id,
    ADD COLUMN actor_id BIGINT UNSIGNED NULL AFTER actor_type,
    ADD COLUMN actor_role_snapshot VARCHAR(200) NULL AFTER actor_id,
    ADD COLUMN reason_code VARCHAR(80) NULL AFTER outcome,
    ADD COLUMN reason_text VARCHAR(1000) NULL AFTER reason_code,
    ADD COLUMN before_json JSON NULL AFTER reason_text,
    ADD COLUMN after_json JSON NULL AFTER before_json,
    ADD COLUMN device_id VARCHAR(128) NULL AFTER request_id,
    ADD COLUMN occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER device_id,
    ADD KEY idx_audit_actor_time (actor_type,actor_id,occurred_at),
    ADD CONSTRAINT ck_audit_actor_type CHECK (actor_type IN ('OPERATOR','DRIVER','SYSTEM','PARTNER'));

UPDATE operation_audit_log
SET actor_id=operator_user_id
WHERE actor_type='OPERATOR' AND actor_id IS NULL;
