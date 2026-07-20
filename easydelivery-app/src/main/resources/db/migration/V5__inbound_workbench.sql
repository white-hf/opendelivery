ALTER TABLE operational_case
    ADD COLUMN inbound_manifest_id BIGINT UNSIGNED NULL AFTER parcel_id,
    ADD COLUMN manifest_item_id BIGINT UNSIGNED NULL AFTER inbound_manifest_id,
    ADD KEY idx_case_manifest_status (inbound_manifest_id, status),
    ADD CONSTRAINT fk_case_manifest FOREIGN KEY (inbound_manifest_id) REFERENCES inbound_manifest (id),
    ADD CONSTRAINT fk_case_manifest_item FOREIGN KEY (manifest_item_id) REFERENCES inbound_manifest_item (id);

CREATE TABLE inbound_scan_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    manifest_id BIGINT UNSIGNED NOT NULL,
    device_event_id VARCHAR(160) NOT NULL,
    tracking_no VARCHAR(128) NOT NULL,
    condition_code VARCHAR(24) NOT NULL DEFAULT 'NORMAL',
    outcome VARCHAR(24) NOT NULL,
    manifest_item_id BIGINT UNSIGNED NULL,
    operator_user_id BIGINT UNSIGNED NULL,
    occurred_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_inbound_device_event (manifest_id, device_event_id),
    KEY idx_inbound_scan_tracking (tracking_no, occurred_at),
    CONSTRAINT fk_inbound_scan_manifest FOREIGN KEY (manifest_id) REFERENCES inbound_manifest (id),
    CONSTRAINT fk_inbound_scan_item FOREIGN KEY (manifest_item_id) REFERENCES inbound_manifest_item (id),
    CONSTRAINT fk_inbound_scan_operator FOREIGN KEY (operator_user_id) REFERENCES operator_user (id),
    CONSTRAINT ck_inbound_scan_condition CHECK (condition_code IN ('NORMAL','DAMAGED')),
    CONSTRAINT ck_inbound_scan_outcome CHECK (outcome IN ('RECEIVED','DAMAGED','EXTRA','WRONG_STATION','DUPLICATE'))
) ENGINE=InnoDB;

