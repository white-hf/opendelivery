-- Flyway Migration: Decommission delivery_area_version table & flatten area data model directly to delivery_area

-- 1. Add boundary and geojson_snapshot columns directly to delivery_area table
ALTER TABLE delivery_area
    ADD COLUMN boundary MULTIPOLYGON NULL SRID 4326 AFTER area_level,
    ADD COLUMN geojson_snapshot JSON NULL AFTER boundary;

-- 2. Populate boundary and geojson_snapshot from the latest delivery_area_version
UPDATE delivery_area a
JOIN (
    SELECT v.delivery_area_id, v.boundary, v.geojson_snapshot
    FROM delivery_area_version v
    INNER JOIN (
        SELECT delivery_area_id, MAX(version_no) max_ver
        FROM delivery_area_version
        GROUP BY delivery_area_id
    ) latest ON v.delivery_area_id = latest.delivery_area_id AND v.version_no = latest.max_ver
) src ON a.id = src.delivery_area_id
SET a.boundary = src.boundary,
    a.geojson_snapshot = src.geojson_snapshot;

-- Add spatial index after data sync (and handle null geometries if any exist in fresh DB)
UPDATE delivery_area SET boundary = ST_GeomFromText('MULTIPOLYGON(((0 0, 0 0.0001, 0.0001 0.0001, 0.0001 0, 0 0)))', 4326) WHERE boundary IS NULL;

ALTER TABLE delivery_area
    MODIFY COLUMN boundary MULTIPOLYGON NOT NULL SRID 4326,
    ADD SPATIAL INDEX idx_delivery_area_boundary (boundary);

-- 3. Refactor parcel table: rename current_area_version_id to current_area_id & update FK to delivery_area
ALTER TABLE parcel
    DROP FOREIGN KEY fk_parcel_area_version,
    DROP INDEX idx_parcel_station_area;

-- Map existing current_area_version_id values to delivery_area_id
UPDATE parcel p
JOIN delivery_area_version v ON v.id = p.current_area_version_id
SET p.current_area_version_id = v.delivery_area_id;

ALTER TABLE parcel
    CHANGE COLUMN current_area_version_id current_area_id BIGINT UNSIGNED NULL,
    ADD KEY idx_parcel_station_area (current_station_id, current_area_id),
    ADD CONSTRAINT fk_parcel_area FOREIGN KEY (current_area_id) REFERENCES delivery_area(id);

-- 3. Refactor parcel_area_assignment table: rename delivery_area_version_id to delivery_area_id & update FK
ALTER TABLE parcel_area_assignment
    DROP FOREIGN KEY fk_assignment_area_version,
    DROP INDEX idx_area_assignment_version;

UPDATE parcel_area_assignment paa
JOIN delivery_area_version v ON v.id = paa.delivery_area_version_id
SET paa.delivery_area_version_id = v.delivery_area_id;

ALTER TABLE parcel_area_assignment
    CHANGE COLUMN delivery_area_version_id delivery_area_id BIGINT UNSIGNED NOT NULL,
    ADD KEY idx_area_assignment_area (delivery_area_id, ended_at, parcel_id),
    ADD CONSTRAINT fk_assignment_area FOREIGN KEY (delivery_area_id) REFERENCES delivery_area(id);

-- 4. Refactor driver_task_area table: rename delivery_area_version_id to delivery_area_id & update FK
ALTER TABLE driver_task_area
    DROP FOREIGN KEY fk_task_area_version,
    DROP INDEX idx_task_area_version;

UPDATE driver_task_area dta
JOIN delivery_area_version v ON v.id = dta.delivery_area_version_id
SET dta.delivery_area_version_id = v.delivery_area_id;

ALTER TABLE driver_task_area
    CHANGE COLUMN delivery_area_version_id delivery_area_id BIGINT UNSIGNED NOT NULL,
    DROP INDEX uk_task_area,
    ADD UNIQUE KEY uk_task_area (task_id, delivery_area_id),
    ADD KEY idx_task_area (delivery_area_id, task_id),
    ADD CONSTRAINT fk_task_area FOREIGN KEY (delivery_area_id) REFERENCES delivery_area(id);

-- 5. Drop the obsolete delivery_area_version table
DROP TABLE IF EXISTS delivery_area_version;
