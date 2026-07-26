-- V18: Create handling unit area rule template table for persistent area-to-unit mappings
CREATE TABLE IF NOT EXISTS handling_unit_area_rule (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    station_id BIGINT UNSIGNED NOT NULL,
    unit_code VARCHAR(64) NOT NULL,
    delivery_area_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_station_unit_area (station_id, unit_code, delivery_area_id),
    CONSTRAINT fk_unit_area_rule_station FOREIGN KEY (station_id) REFERENCES station(id) ON DELETE CASCADE,
    CONSTRAINT fk_unit_area_rule_area FOREIGN KEY (delivery_area_id) REFERENCES delivery_area(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;




