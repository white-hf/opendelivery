-- Seed data for testing Map Planning & One-click Assign Defaults across Canada 3 Cities:
-- Station 1 (Halifax - YHZ01), Station 2 (Toronto - YYZ01), Station 3 (Vancouver - YVR01)

-- 1. Ensure 3 City Hub Stations exist
INSERT INTO station (id, station_code, station_name, city, province_code, country_code, timezone, address_line, status)
VALUES 
(1, 'YHZ01', 'Halifax Transit Hub', 'Halifax', 'NS', 'CA', 'America/Halifax', '100 Logistics Way, Halifax', 'ACTIVE'),
(2, 'YYZ01', 'Greater Toronto Sorting Hub', 'Toronto', 'ON', 'CA', 'America/Toronto', '200 Airport Rd, Toronto', 'ACTIVE'),
(3, 'YVR01', 'Metro Vancouver Hub', 'Vancouver', 'BC', 'CA', 'America/Vancouver', '300 Marine Dr, Vancouver', 'ACTIVE')
ON DUPLICATE KEY UPDATE status = 'ACTIVE';

-- 2. Ensure Drivers exist for 3 City Stations
INSERT INTO driver (id, home_station_id, credential_id, password_hash, driver_name, phone, status)
VALUES 
(101, 1, 'driver_yhz', '$2a$10$w8.L...3g...', 'Halifax Express Driver', '19020000001', 'ACTIVE'),
(102, 2, 'driver_yyz', '$2a$10$w8.L...3g...', 'Toronto Express Driver', '14160000002', 'ACTIVE'),
(103, 3, 'driver_yvr', '$2a$10$w8.L...3g...', 'Vancouver Express Driver', '16040000003', 'ACTIVE')
ON DUPLICATE KEY UPDATE status = 'ACTIVE';

-- 3. Ensure Delivery Areas exist for 3 City Hubs
INSERT INTO delivery_area (id, station_id, area_code, area_name, area_level, status, boundary)
VALUES 
(1001, 1, 'AREA-YHZ-01', 'Halifax Downtown Core', 1, 'ACTIVE', ST_GeomFromText('MULTIPOLYGON(((0 0, 0 0.1, 0.1 0.1, 0.1 0, 0 0)))', 4326)),
(1002, 2, 'AREA-YYZ-01', 'Toronto Downtown Core', 1, 'ACTIVE', ST_GeomFromText('MULTIPOLYGON(((0 0, 0 0.1, 0.1 0.1, 0.1 0, 0 0)))', 4326)),
(1003, 3, 'AREA-YVR-01', 'Vancouver Downtown Core', 1, 'ACTIVE', ST_GeomFromText('MULTIPOLYGON(((0 0, 0 0.1, 0.1 0.1, 0.1 0, 0 0)))', 4326))
ON DUPLICATE KEY UPDATE status = 'ACTIVE';

-- 4. Ensure Driver Area Preferences exist
INSERT INTO driver_area_preference (driver_id, delivery_area_id, priority, status)
VALUES 
(101, 1001, 1, 'ACTIVE'),
(102, 1002, 1, 'ACTIVE'),
(103, 1003, 1, 'ACTIVE')
ON DUPLICATE KEY UPDATE status = 'ACTIVE';

-- 5. Ensure Driver Shifts for current date
INSERT INTO driver_shift (station_id, driver_id, service_date, availability_status, parcel_capacity)
VALUES 
(1, 101, CURRENT_DATE(), 'AVAILABLE', 200),
(2, 102, CURRENT_DATE(), 'AVAILABLE', 200),
(3, 103, CURRENT_DATE(), 'AVAILABLE', 200)
ON DUPLICATE KEY UPDATE availability_status = 'AVAILABLE', parcel_capacity = 200;

-- 6. Insert Upstream Partner & Ingestion Batch if missing
INSERT INTO upstream_partner (id, partner_code, partner_name, integration_mode, status)
VALUES (1, 'PARTNER_DEMO', 'Demo Logistics Partner', 'PUSH', 'ACTIVE')
ON DUPLICATE KEY UPDATE status = 'ACTIVE';

INSERT INTO ingestion_batch (id, partner_id, external_batch_no, source_type, status, received_count, accepted_count)
VALUES (1001, 1, 'BATCH-DEMO-20260725', 'PUSH', 'COMPLETED', 15, 15)
ON DUPLICATE KEY UPDATE status = 'COMPLETED';

-- 7. Insert Demo Waybills & Parcels for Halifax, Toronto, and Vancouver
INSERT INTO waybill (id, partner_id, external_waybill_no, routing_status, resolved_station_id, recipient_name, recipient_phone, address_line1, city, province, postal_code)
VALUES 
(8001, 1, 'WB-YHZ-0001', 'ROUTED', 1, 'Alice (Halifax)', '19021112222', '123 Barrington St', 'Halifax', 'NS', 'B3J 1Y2'),
(8002, 1, 'WB-YHZ-0002', 'ROUTED', 1, 'Bob (Halifax)', '19023334444', '456 Spring Garden Rd', 'Halifax', 'NS', 'B3J 1G1'),
(8003, 1, 'WB-YYZ-0001', 'ROUTED', 2, 'Charlie (Toronto)', '14165556666', '789 Yonge St', 'Toronto', 'ON', 'M4Y 2B8'),
(8004, 1, 'WB-YYZ-0002', 'ROUTED', 2, 'David (Toronto)', '14167778888', '101 Bay St', 'Toronto', 'ON', 'M5J 2S1'),
(8005, 1, 'WB-YVR-0001', 'ROUTED', 3, 'Eve (Vancouver)', '16049990000', '202 Robson St', 'Vancouver', 'BC', 'V6B 3K9')
ON DUPLICATE KEY UPDATE routing_status = 'ROUTED';

-- 7b. Insert Waybill Geocoding Points (Latitude, Longitude for SRID 4326) for Map Planning
INSERT INTO waybill_geocode (waybill_id, delivery_point, provider_code, precision_code, geocoded_at)
VALUES 
(8001, ST_GeomFromText('POINT(44.6488 -63.5752)', 4326), 'NOMINATIM', 'ROOFTOP', CURRENT_TIMESTAMP()),
(8002, ST_GeomFromText('POINT(44.6441 -63.5823)', 4326), 'NOMINATIM', 'ROOFTOP', CURRENT_TIMESTAMP()),
(8003, ST_GeomFromText('POINT(43.6629 -79.3871)', 4326), 'NOMINATIM', 'ROOFTOP', CURRENT_TIMESTAMP()),
(8004, ST_GeomFromText('POINT(43.6487 -79.3813)', 4326), 'NOMINATIM', 'ROOFTOP', CURRENT_TIMESTAMP()),
(8005, ST_GeomFromText('POINT(49.2827 -123.1162)', 4326), 'NOMINATIM', 'ROOFTOP', CURRENT_TIMESTAMP())
ON DUPLICATE KEY UPDATE geocoded_at = CURRENT_TIMESTAMP();

INSERT INTO parcel (id, waybill_id, tracking_no, current_station_id, current_area_id, status, promised_date, current_custody_type)
VALUES 
(8001, 8001, 'TRK-YHZ-0001', 1, 1001, 'READY_FOR_DISPATCH', CURRENT_DATE(), 'STATION'),
(8002, 8002, 'TRK-YHZ-0002', 1, 1001, 'READY_FOR_DISPATCH', CURRENT_DATE(), 'STATION'),
(8003, 8003, 'TRK-YYZ-0001', 2, 1002, 'READY_FOR_DISPATCH', CURRENT_DATE(), 'STATION'),
(8004, 8004, 'TRK-YYZ-0002', 2, 1002, 'READY_FOR_DISPATCH', CURRENT_DATE(), 'STATION'),
(8005, 8005, 'TRK-YVR-0001', 3, 1003, 'READY_FOR_DISPATCH', CURRENT_DATE(), 'STATION')
ON DUPLICATE KEY UPDATE status = 'READY_FOR_DISPATCH', promised_date = CURRENT_DATE();


-- 8. Link Parcels to Delivery Area Assignments
INSERT INTO parcel_area_assignment (parcel_id, delivery_area_id, assignment_source, assigned_at)
VALUES 
(8001, 1001, 'GEO_POLYGON', CURRENT_TIMESTAMP()),
(8002, 1001, 'GEO_POLYGON', CURRENT_TIMESTAMP()),
(8003, 1002, 'GEO_POLYGON', CURRENT_TIMESTAMP()),
(8004, 1002, 'GEO_POLYGON', CURRENT_TIMESTAMP()),
(8005, 1003, 'GEO_POLYGON', CURRENT_TIMESTAMP())
ON DUPLICATE KEY UPDATE ended_at = NULL;

