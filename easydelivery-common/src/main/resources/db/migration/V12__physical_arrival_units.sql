CREATE TABLE arrival_trip (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    station_id BIGINT UNSIGNED NOT NULL,
    external_trip_no VARCHAR(100) NOT NULL,
    upstream_partner_id BIGINT UNSIGNED NULL,
    vehicle_plate VARCHAR(40) NULL,
    seal_no VARCHAR(80) NULL,
    expected_at DATETIME(3) NULL,
    arrived_at DATETIME(3) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'EXPECTED',
    note VARCHAR(500) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_arrival_trip_station_no (station_id,external_trip_no),
    KEY idx_arrival_trip_station_status_time (station_id,status,expected_at),
    CONSTRAINT fk_arrival_trip_station FOREIGN KEY (station_id) REFERENCES station(id),
    CONSTRAINT fk_arrival_trip_partner FOREIGN KEY (upstream_partner_id) REFERENCES upstream_partner(id),
    CONSTRAINT ck_arrival_trip_status CHECK (status IN ('EXPECTED','ARRIVED','UNLOADING','READY_FOR_SCAN','CLOSED','CANCELLED'))
) ENGINE=InnoDB;

CREATE TABLE handling_unit (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    trip_id BIGINT UNSIGNED NOT NULL,
    station_id BIGINT UNSIGNED NOT NULL,
    external_unit_no VARCHAR(100) NOT NULL,
    unit_type VARCHAR(24) NOT NULL,
    expected_piece_count INT UNSIGNED NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'EXPECTED',
    arrived_at DATETIME(3) NULL,
    opened_at DATETIME(3) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_handling_unit_station_no (station_id,external_unit_no),
    KEY idx_handling_unit_trip_status (trip_id,status),
    CONSTRAINT fk_handling_unit_trip FOREIGN KEY (trip_id) REFERENCES arrival_trip(id),
    CONSTRAINT fk_handling_unit_station FOREIGN KEY (station_id) REFERENCES station(id),
    CONSTRAINT ck_handling_unit_type CHECK (unit_type IN ('PALLET','CAGE','BAG','LOOSE')),
    CONSTRAINT ck_handling_unit_status CHECK (status IN ('EXPECTED','ARRIVED','OPENED','CLEARED'))
) ENGINE=InnoDB;

CREATE TABLE handling_unit_parcel (
    handling_unit_id BIGINT UNSIGNED NOT NULL,
    parcel_id BIGINT UNSIGNED NOT NULL,
    link_source VARCHAR(24) NOT NULL DEFAULT 'UPSTREAM',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (handling_unit_id,parcel_id),
    KEY idx_unit_parcel_parcel (parcel_id,handling_unit_id),
    CONSTRAINT fk_unit_parcel_unit FOREIGN KEY (handling_unit_id) REFERENCES handling_unit(id),
    CONSTRAINT fk_unit_parcel_parcel FOREIGN KEY (parcel_id) REFERENCES parcel(id),
    CONSTRAINT ck_unit_parcel_source CHECK (link_source IN ('UPSTREAM','OPERATOR','SCAN_OBSERVED'))
) ENGINE=InnoDB;
