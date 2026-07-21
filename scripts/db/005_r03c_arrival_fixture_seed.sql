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

INSERT INTO delivery_area (station_id,area_code,area_name,area_level,status)
SELECT s.id, seed.area_code, seed.area_name, 1, 'ACTIVE'
FROM (
    SELECT 'YHZ-01' station_code,'PILOT-A' area_code,'Pilot Area A (West)' area_name
    UNION ALL SELECT 'YHZ-01','PILOT-B','Pilot Area B (East)'
    UNION ALL SELECT 'YYZ-01','PILOT-A','Pilot Area A (West)'
    UNION ALL SELECT 'YYZ-01','PILOT-B','Pilot Area B (East)'
    UNION ALL SELECT 'YVR-01','PILOT-A','Pilot Area A (West)'
    UNION ALL SELECT 'YVR-01','PILOT-B','Pilot Area B (East)'
) seed JOIN station s ON s.station_code=seed.station_code
WHERE NOT EXISTS (
    SELECT 1 FROM delivery_area a WHERE a.station_id=s.id AND a.area_code=seed.area_code
);

INSERT INTO delivery_area_version
    (delivery_area_id,version_no,status,boundary,geojson_snapshot,effective_from,change_reason)
SELECT a.id, 1, 'PUBLISHED',
       ST_GeomFromGeoJSON(seed.geo_json,1,4326),
       CAST(seed.geo_json AS JSON),
       CURRENT_TIMESTAMP(3),
       'R03-C pilot fixture seed'
FROM (
    SELECT 'YHZ-01' station_code,'PILOT-A' area_code,
           '{"type":"Polygon","coordinates":[[[-63.62,44.62],[-63.60,44.62],[-63.60,44.64],[-63.62,44.64],[-63.62,44.62]]]}' geo_json
    UNION ALL SELECT 'YHZ-01','PILOT-B',
           '{"type":"Polygon","coordinates":[[[-63.60,44.62],[-63.58,44.62],[-63.58,44.64],[-63.60,44.64],[-63.60,44.62]]]}'
    UNION ALL SELECT 'YYZ-01','PILOT-A',
           '{"type":"Polygon","coordinates":[[[-79.42,43.64],[-79.40,43.64],[-79.40,43.66],[-79.42,43.66],[-79.42,43.64]]]}'
    UNION ALL SELECT 'YYZ-01','PILOT-B',
           '{"type":"Polygon","coordinates":[[[-79.40,43.64],[-79.38,43.64],[-79.38,43.66],[-79.40,43.66],[-79.40,43.64]]]}'
    UNION ALL SELECT 'YVR-01','PILOT-A',
           '{"type":"Polygon","coordinates":[[[-123.14,49.26],[-123.12,49.26],[-123.12,49.28],[-123.14,49.28],[-123.14,49.26]]]}'
    UNION ALL SELECT 'YVR-01','PILOT-B',
           '{"type":"Polygon","coordinates":[[[-123.12,49.26],[-123.10,49.26],[-123.10,49.28],[-123.12,49.28],[-123.12,49.26]]]}'
) seed
JOIN station s ON s.station_code=seed.station_code
JOIN delivery_area a ON a.station_id=s.id AND a.area_code=seed.area_code
WHERE NOT EXISTS (
    SELECT 1 FROM delivery_area_version v WHERE v.delivery_area_id=a.id AND v.version_no=1
);
