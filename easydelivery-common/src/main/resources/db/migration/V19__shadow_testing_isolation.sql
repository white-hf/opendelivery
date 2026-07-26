-- V19: Add shadow testing isolation fields (is_test / is_test_driver) for production E2E testing
ALTER TABLE waybill ADD COLUMN is_test TINYINT NOT NULL DEFAULT 0 COMMENT '0-real, 1-shadow test';
ALTER TABLE parcel ADD COLUMN is_test TINYINT NOT NULL DEFAULT 0 COMMENT '0-real, 1-shadow test';
CREATE INDEX idx_parcel_station_test ON parcel(current_station_id, is_test, status);

ALTER TABLE dispatch_wave ADD COLUMN is_test TINYINT NOT NULL DEFAULT 0 COMMENT '0-real, 1-shadow test';
ALTER TABLE handling_unit ADD COLUMN is_test TINYINT NOT NULL DEFAULT 0 COMMENT '0-real, 1-shadow test';

ALTER TABLE driver ADD COLUMN is_test_driver TINYINT NOT NULL DEFAULT 0 COMMENT '0-real, 1-shadow test driver';
ALTER TABLE driver_task ADD COLUMN is_test TINYINT NOT NULL DEFAULT 0 COMMENT '0-real, 1-shadow test';
ALTER TABLE driver_task_item ADD COLUMN is_test TINYINT NOT NULL DEFAULT 0 COMMENT '0-real, 1-shadow test';

ALTER TABLE scan_session ADD COLUMN is_test TINYINT NOT NULL DEFAULT 0 COMMENT '0-real, 1-shadow test';
ALTER TABLE scan_event ADD COLUMN is_test TINYINT NOT NULL DEFAULT 0 COMMENT '0-real, 1-shadow test';

ALTER TABLE delivery_attempt ADD COLUMN is_test TINYINT NOT NULL DEFAULT 0 COMMENT '0-real, 1-shadow test';
ALTER TABLE operational_case ADD COLUMN is_test TINYINT NOT NULL DEFAULT 0 COMMENT '0-real, 1-shadow test';
