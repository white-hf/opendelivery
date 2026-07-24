CREATE TABLE driver_hold_approval (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    parcel_id BIGINT UNSIGNED NOT NULL,
    driver_id BIGINT UNSIGNED NOT NULL,
    station_id BIGINT UNSIGNED NOT NULL,
    approved_by BIGINT UNSIGNED NULL,
    reason_code VARCHAR(64) NOT NULL,
    reason_text VARCHAR(500) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'APPROVED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_hold_parcel (parcel_id),
    KEY idx_hold_driver_status (driver_id, status)
) ENGINE=InnoDB;
