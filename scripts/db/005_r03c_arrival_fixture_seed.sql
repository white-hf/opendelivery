-- R03-C pilot fixture (non-production). Apply after 003_three_station_pilot_seed.sql.
-- Adds a second pilot driver per station and two published delivery areas per station,
-- so arrival-batch area fill and cross-driver coverage can be exercised immediately.
-- Idempotent; rectangles are small synthetic polygons near each pilot city.

INSERT INTO driver (home_station_id,credential_id,password_hash,driver_name,status)
SELECT s.id,
       CONCAT('pilot.driver2.',LOWER(LEFT(s.station_code,3))),
       '$2a$10$8QOXb3qUhBVIecy5luJH6emzRfxlrxbCjc2YnUPqeb/RqKQReHH3.',
       CONCAT(s.city,' Pilot Driver 2'),'ACTIVE'
FROM station s
WHERE s.station_code IN ('YHZ-01','YYZ-01','YVR-01')
  AND NOT EXISTS (
      SELECT 1 FROM driver d
      WHERE d.credential_id=CONCAT('pilot.driver2.',LOWER(LEFT(s.station_code,3)))
  );

INSERT INTO delivery_area (station_id,area_code,area_name,area_level,status,boundary,geojson_snapshot)
SELECT s.id, seed.area_code, seed.area_name, 1, 'ACTIVE',
       ST_GeomFromGeoJSON(seed.geo_json,1,4326),
       CAST(seed.geo_json AS JSON)
FROM (
    SELECT 'YHZ-01' station_code,'PILOT-A' area_code,'Pilot Area A (West)' area_name,
           '{"type":"MultiPolygon","coordinates":[[[[-63.62,44.62],[-63.60,44.62],[-63.60,44.64],[-63.62,44.64],[-63.62,44.62]]]]}' geo_json
    UNION ALL SELECT 'YHZ-01','PILOT-B','Pilot Area B (East)',
           '{"type":"MultiPolygon","coordinates":[[[[-63.60,44.62],[-63.58,44.62],[-63.58,44.64],[-63.60,44.64],[-63.60,44.62]]]]}'
    UNION ALL SELECT 'YYZ-01','PILOT-A','Pilot Area A (West)',
           '{"type":"MultiPolygon","coordinates":[[[[-79.42,43.64],[-79.40,43.64],[-79.40,43.66],[-79.42,43.66],[-79.42,43.64]]]]}'
    UNION ALL SELECT 'YYZ-01','PILOT-B','Pilot Area B (East)',
           '{"type":"MultiPolygon","coordinates":[[[[-79.40,43.64],[-79.38,43.64],[-79.38,43.66],[-79.40,43.66],[-79.40,43.64]]]]}'
    UNION ALL SELECT 'YVR-01','PILOT-A','Pilot Area A (West)',
           '{"type":"MultiPolygon","coordinates":[[[[-123.14,49.26],[-123.12,49.26],[-123.12,49.28],[-123.14,49.28],[-123.14,49.26]]]]}'
    UNION ALL SELECT 'YVR-01','PILOT-B','Pilot Area B (East)',
           '{"type":"MultiPolygon","coordinates":[[[[-123.10,49.26],[-123.12,49.26],[-123.12,49.28],[-123.10,49.28],[-123.10,49.26]]]]}'
) seed JOIN station s ON s.station_code=seed.station_code
ON DUPLICATE KEY UPDATE area_name=VALUES(area_name),status='ACTIVE',boundary=VALUES(boundary),geojson_snapshot=VALUES(geojson_snapshot);
