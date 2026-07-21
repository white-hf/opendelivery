ALTER TABLE parcel
    ADD COLUMN upstream_unit_no VARCHAR(100) NULL AFTER promised_date,
    ADD COLUMN current_area_version_id BIGINT UNSIGNED NULL AFTER upstream_unit_no,
    ADD KEY idx_parcel_unit_station (upstream_unit_no, current_station_id),
    ADD KEY idx_parcel_station_area (current_station_id, current_area_version_id),
    ADD CONSTRAINT fk_parcel_area_version FOREIGN KEY (current_area_version_id) REFERENCES delivery_area_version(id);

UPDATE parcel p JOIN parcel_area_assignment paa
    ON paa.parcel_id=p.id AND paa.ended_at IS NULL
SET p.current_area_version_id=paa.delivery_area_version_id;

ALTER TABLE handling_unit_parcel
    DROP CHECK ck_unit_parcel_source,
    ADD CONSTRAINT ck_unit_parcel_source CHECK (link_source IN ('UPSTREAM','OPERATOR','SCAN_OBSERVED','AREA_PLAN'));
