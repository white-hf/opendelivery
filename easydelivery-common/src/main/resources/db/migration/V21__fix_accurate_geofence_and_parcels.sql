-- Update Delivery Area Geofence Boundaries to actual Canadian city downtown coordinates:
-- Halifax Downtown: (-63.575, 44.648)
-- Toronto Downtown: (-79.387, 43.653)
-- Vancouver Downtown: (-123.116, 49.282)

-- 0. Ensure Station Code Aliases exist for YHZ-01 / YYZ-01 / YVR-01
INSERT INTO station (id, station_code, station_name, city, province_code, country_code, timezone, address_line, status)
VALUES 
(1, 'YHZ01', 'Halifax Transit Hub', 'Halifax', 'NS', 'CA', 'America/Halifax', '100 Logistics Way, Halifax', 'ACTIVE'),
(2, 'YYZ01', 'Greater Toronto Sorting Hub', 'Toronto', 'ON', 'CA', 'America/Toronto', '200 Airport Rd, Toronto', 'ACTIVE'),
(3, 'YVR01', 'Metro Vancouver Hub', 'Vancouver', 'BC', 'CA', 'America/Vancouver', '300 Marine Dr, Vancouver', 'ACTIVE')
ON DUPLICATE KEY UPDATE status = 'ACTIVE';

-- Add aliases for hyphenated code format
UPDATE station SET station_code = 'YHZ-01' WHERE id = 1;
UPDATE station SET station_code = 'YYZ-01' WHERE id = 2;
UPDATE station SET station_code = 'YVR-01' WHERE id = 3;

UPDATE delivery_area 
SET boundary = ST_GeomFromText('MULTIPOLYGON(((44.62 -63.62, 44.68 -63.62, 44.68 -63.54, 44.62 -63.54, 44.62 -63.62)))', 4326),
    geojson_snapshot = '{"type":"Polygon","coordinates":[[[-63.62,44.62],[-63.62,44.68],[-63.54,44.68],[-63.54,44.62],[-63.62,44.62]]]}'
WHERE id = 1001 OR area_code = 'AREA-YHZ-01';

UPDATE delivery_area 
SET boundary = ST_GeomFromText('MULTIPOLYGON(((43.62 -79.42, 43.68 -79.42, 43.68 -79.35, 43.62 -79.35, 43.62 -79.42)))', 4326),
    geojson_snapshot = '{"type":"Polygon","coordinates":[[[-79.42,43.62],[-79.42,43.68],[-79.35,43.68],[-79.35,43.62],[-79.42,43.62]]]}'
WHERE id = 1002 OR area_code = 'AREA-YYZ-01';

UPDATE delivery_area 
SET boundary = ST_GeomFromText('MULTIPOLYGON(((49.25 -123.15, 49.31 -123.15, 49.31 -123.08, 49.25 -123.08, 49.25 -123.15)))', 4326),
    geojson_snapshot = '{"type":"Polygon","coordinates":[[[-123.15,49.25],[-123.15,49.31],[-123.08,49.31],[-123.08,49.25],[-123.15,49.25]]]}'
WHERE id = 1003 OR area_code = 'AREA-YVR-01';


-- Update Geocode points to ensure 100% precision inside the polygons
UPDATE waybill_geocode SET delivery_point = ST_GeomFromText('POINT(44.6488 -63.5752)', 4326) WHERE waybill_id = 8001;
UPDATE waybill_geocode SET delivery_point = ST_GeomFromText('POINT(44.6441 -63.5823)', 4326) WHERE waybill_id = 8002;
UPDATE waybill_geocode SET delivery_point = ST_GeomFromText('POINT(43.6629 -79.3871)', 4326) WHERE waybill_id = 8003;
UPDATE waybill_geocode SET delivery_point = ST_GeomFromText('POINT(43.6487 -79.3813)', 4326) WHERE waybill_id = 8004;
UPDATE waybill_geocode SET delivery_point = ST_GeomFromText('POINT(49.2827 -123.1162)', 4326) WHERE waybill_id = 8005;

-- Insert additional accurate parcels inside Halifax AREA-YHZ-01
INSERT INTO waybill (id, partner_id, external_waybill_no, routing_status, resolved_station_id, recipient_name, recipient_phone, address_line1, city, province, postal_code)
VALUES 
(8006, 1, 'WB-YHZ-0003', 'ROUTED', 1, 'Grace (Halifax)', '19028889999', '1505 Barrington St', 'Halifax', 'NS', 'B3J 3K5'),
(8007, 1, 'WB-YHZ-0004', 'ROUTED', 1, 'Henry (Halifax)', '19027776666', '5410 Spring Garden Rd', 'Halifax', 'NS', 'B3J 1H6')
ON DUPLICATE KEY UPDATE routing_status = 'ROUTED';

INSERT INTO waybill_geocode (waybill_id, delivery_point, provider_code, precision_code, geocoded_at)
VALUES 
(8006, ST_GeomFromText('POINT(44.6468 -63.5732)', 4326), 'NOMINATIM', 'ROOFTOP', CURRENT_TIMESTAMP()),
(8007, ST_GeomFromText('POINT(44.6435 -63.5801)', 4326), 'NOMINATIM', 'ROOFTOP', CURRENT_TIMESTAMP())
ON DUPLICATE KEY UPDATE geocoded_at = CURRENT_TIMESTAMP();

INSERT INTO parcel (id, waybill_id, tracking_no, current_station_id, current_area_id, status, promised_date, current_custody_type)
VALUES 
(8006, 8006, 'TRK-YHZ-0003', 1, 1001, 'READY_FOR_DISPATCH', CURRENT_DATE(), 'STATION'),
(8007, 8007, 'TRK-YHZ-0004', 1, 1001, 'READY_FOR_DISPATCH', CURRENT_DATE(), 'STATION')
ON DUPLICATE KEY UPDATE status = 'READY_FOR_DISPATCH', promised_date = CURRENT_DATE();

INSERT INTO parcel_area_assignment (parcel_id, delivery_area_id, assignment_source, assigned_at)
VALUES 
(8006, 1001, 'GEO_POLYGON', CURRENT_TIMESTAMP()),
(8007, 1001, 'GEO_POLYGON', CURRENT_TIMESTAMP())
ON DUPLICATE KEY UPDATE ended_at = NULL;
