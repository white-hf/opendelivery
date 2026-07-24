ALTER TABLE dispatch_wave DROP CHECK ck_wave_status;
ALTER TABLE dispatch_wave
    ADD COLUMN frozen_at DATETIME(3) NULL AFTER status,
    ADD COLUMN frozen_by BIGINT UNSIGNED NULL AFTER frozen_at,
    ADD CONSTRAINT fk_wave_frozen_by FOREIGN KEY (frozen_by) REFERENCES operator_user(id),
    ADD CONSTRAINT ck_wave_status CHECK (status IN ('DRAFT','FROZEN','PUBLISHED','IN_PROGRESS','CLOSED','CANCELLED'));

ALTER TABLE driver_task DROP CHECK ck_task_status;
ALTER TABLE driver_task
    ADD CONSTRAINT ck_task_status CHECK (status IN ('DRAFT','FROZEN','PUBLISHED','ACCEPTING','IN_PROGRESS','CLOSED','CANCELLED'));

CREATE TABLE driver_shift (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    station_id BIGINT UNSIGNED NOT NULL,
    driver_id BIGINT UNSIGNED NOT NULL,
    service_date DATE NOT NULL,
    availability_status VARCHAR(24) NOT NULL DEFAULT 'AVAILABLE',
    parcel_capacity INT UNSIGNED NOT NULL,
    note VARCHAR(500) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_driver_shift_date (driver_id,service_date),
    KEY idx_shift_station_date_status (station_id,service_date,availability_status),
    CONSTRAINT fk_shift_station FOREIGN KEY (station_id) REFERENCES station(id),
    CONSTRAINT fk_shift_driver FOREIGN KEY (driver_id) REFERENCES driver(id),
    CONSTRAINT ck_shift_availability CHECK (availability_status IN ('AVAILABLE','UNAVAILABLE')),
    CONSTRAINT ck_shift_capacity CHECK (parcel_capacity BETWEEN 1 AND 1000)
) ENGINE=InnoDB;

CREATE TABLE driver_task_area (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    task_id BIGINT UNSIGNED NOT NULL,
    delivery_area_version_id BIGINT UNSIGNED NOT NULL,
    assignment_mode VARCHAR(24) NOT NULL,
    assigned_by BIGINT UNSIGNED NULL,
    assigned_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_area (task_id,delivery_area_version_id),
    KEY idx_task_area_version (delivery_area_version_id,task_id),
    CONSTRAINT fk_task_area_task FOREIGN KEY (task_id) REFERENCES driver_task(id),
    CONSTRAINT fk_task_area_version FOREIGN KEY (delivery_area_version_id) REFERENCES delivery_area_version(id),
    CONSTRAINT fk_task_area_operator FOREIGN KEY (assigned_by) REFERENCES operator_user(id),
    CONSTRAINT ck_task_area_mode CHECK (assignment_mode IN ('WHOLE_AREA','PARTIAL_AREA'))
) ENGINE=InnoDB;

CREATE INDEX idx_task_station_date_status ON driver_task(station_id,service_date,status,driver_id);
