-- Non-production pilot seed. Apply after Flyway migrations and rotate all seeded credentials.
-- Idempotently ensures the YHZ/YYZ/YVR single-station operating model has staffing records.

INSERT INTO station (station_code,station_name,city,province_code,country_code,timezone,address_line,status)
VALUES
    ('YHZ-01','Halifax Last Mile Station','HALIFAX','NS','CA','America/Halifax','Halifax, NS','ACTIVE'),
    ('YYZ-01','Toronto Last Mile Station','TORONTO','ON','CA','America/Toronto','Toronto, ON','ACTIVE'),
    ('YVR-01','Vancouver Last Mile Station','VANCOUVER','BC','CA','America/Vancouver','Vancouver, BC','ACTIVE')
ON DUPLICATE KEY UPDATE station_name=VALUES(station_name),timezone=VALUES(timezone),status='ACTIVE';

INSERT INTO station_service_area (station_id,country_code,province_code,city_name,postal_prefix,priority,status)
SELECT s.id,'CA',seed.province_code,seed.city_name,NULL,100,'ACTIVE'
FROM (
    SELECT 'YHZ-01' station_code,'NS' province_code,'HALIFAX' city_name
    UNION ALL SELECT 'YYZ-01','ON','TORONTO'
    UNION ALL SELECT 'YVR-01','BC','VANCOUVER'
) seed JOIN station s ON s.station_code=seed.station_code
WHERE NOT EXISTS (
    SELECT 1 FROM station_service_area a WHERE a.station_id=s.id AND a.country_code='CA'
      AND a.province_code=seed.province_code AND a.city_name=seed.city_name AND a.postal_prefix IS NULL
);

INSERT INTO driver (home_station_id,credential_id,password_hash,driver_name,status)
SELECT s.id,LOWER(CONCAT('pilot.driver.',LEFT(s.station_code,3))),
       '$2a$10$x98JYz3ZgYUzuFZbhj1u5.AEbJieTEyYuChW3/dg4bO7iQ8n2pO02',
       CONCAT(s.city,' Pilot Driver'),'ACTIVE'
FROM station s WHERE s.station_code IN ('YHZ-01','YYZ-01','YVR-01')
  AND NOT EXISTS (SELECT 1 FROM driver d WHERE d.home_station_id=s.id AND d.status='ACTIVE');

INSERT INTO operator_user (username,password_hash,display_name,default_station_id,status)
SELECT CONCAT(role_seed.prefix,'.',LOWER(LEFT(s.station_code,3))),
       '$2a$10$x98JYz3ZgYUzuFZbhj1u5.AEbJieTEyYuChW3/dg4bO7iQ8n2pO02',
       CONCAT(s.city,' ',role_seed.label),s.id,'ACTIVE'
FROM station s CROSS JOIN (
    SELECT 'inbound' prefix,'Inbound' label UNION ALL SELECT 'dispatch','Dispatcher'
) role_seed
WHERE s.station_code IN ('YHZ-01','YYZ-01','YVR-01')
  AND NOT EXISTS (
      SELECT 1 FROM operator_user u
      WHERE u.username=CONCAT(role_seed.prefix,'.',LOWER(LEFT(s.station_code,3)))
  );

INSERT IGNORE INTO operator_user_role (user_id,role_id)
SELECT u.id,r.id FROM operator_user u JOIN operator_role r
  ON r.role_code=IF(u.username LIKE 'inbound.%','INBOUND','DISPATCHER')
WHERE u.username IN ('inbound.yhz','dispatch.yhz','inbound.yyz','dispatch.yyz','inbound.yvr','dispatch.yvr');
