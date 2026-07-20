-- R02 non-production experience fixture. Apply after Flyway and 003_three_station_pilot_seed.sql.
-- Idempotent by DEMO-R02-* business keys. It creates 3 cities x 6 drivers x 72 parcels.

INSERT INTO upstream_partner(partner_code,partner_name,integration_mode,status,timezone)
VALUES ('DEMO-R02-UPSTREAM','NorthStar Commerce (Demo)','HYBRID','ACTIVE','America/Halifax')
ON DUPLICATE KEY UPDATE partner_name=VALUES(partner_name),status='ACTIVE';

DROP TEMPORARY TABLE IF EXISTS r02_city;
CREATE TEMPORARY TABLE r02_city(
    station_code VARCHAR(64) PRIMARY KEY, city VARCHAR(100), province VARCHAR(32), postal VARCHAR(8),
    center_lng DECIMAL(10,6), center_lat DECIMAL(10,6)
);
INSERT INTO r02_city VALUES
 ('YHZ-01','HALIFAX','NS','B3H',-63.575200,44.648800),
 ('YYZ-01','TORONTO','ON','M5V',-79.387100,43.642600),
 ('YVR-01','VANCOUVER','BC','V6B',-123.113900,49.279700);

INSERT INTO driver(home_station_id,credential_id,password_hash,driver_name,phone,status)
SELECT s.id,CONCAT('demo.',LOWER(LEFT(c.station_code,3)),'.driver',n.n),
 '$2a$10$8QOXb3qUhBVIecy5luJH6emzRfxlrxbCjc2YnUPqeb/RqKQReHH3.',
 CONCAT(ELT(n.n,'Alex Chen','Maya Singh','Noah Martin','Sofia Garcia','Ethan Wilson','Amelia Brown'),' · ',LEFT(c.station_code,3)),
 CONCAT('+1-555-',LPAD(s.id,3,'0'),'-',LPAD(n.n,4,'0')),'ACTIVE'
FROM r02_city c JOIN station s ON s.station_code=c.station_code
CROSS JOIN (SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6) n
WHERE NOT EXISTS (SELECT 1 FROM driver d WHERE d.credential_id=CONCAT('demo.',LOWER(LEFT(c.station_code,3)),'.driver',n.n));

INSERT INTO driver_shift(station_id,driver_id,service_date,availability_status,parcel_capacity,note)
SELECT d.home_station_id,d.id,CURRENT_DATE,
 IF(RIGHT(d.credential_id,1)='6','UNAVAILABLE','AVAILABLE'),
 ELT(CAST(RIGHT(d.credential_id,1) AS UNSIGNED),22,28,32,26,30,18),'R02 experience fixture'
FROM driver d WHERE d.credential_id LIKE 'demo.%.driver_'
ON DUPLICATE KEY UPDATE availability_status=VALUES(availability_status),parcel_capacity=VALUES(parcel_capacity),note=VALUES(note);

INSERT INTO delivery_area(station_id,area_code,area_name,area_level,status)
SELECT s.id,CONCAT('DEMO-R02-',LEFT(c.station_code,3),'-A',n.n),
 CONCAT(ELT(n.n,'Downtown West','Downtown East','North Residential','South Residential'),' · Demo'),1,'ACTIVE'
FROM r02_city c JOIN station s ON s.station_code=c.station_code
CROSS JOIN (SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) n
WHERE 1=1
ON DUPLICATE KEY UPDATE area_name=VALUES(area_name),status='ACTIVE';

INSERT INTO delivery_area_version(delivery_area_id,version_no,status,boundary,geojson_snapshot,effective_from,validation_json,change_reason,approved_at)
SELECT a.id,1,'PUBLISHED',
 ST_GeomFromText(CONCAT('MULTIPOLYGON(((',
   c.center_lng + IF(n.n IN (1,3),-0.030,0.000),' ',c.center_lat + IF(n.n IN (1,2),0.000,-0.025),',',
   c.center_lng + IF(n.n IN (1,3),0.000,0.030),' ',c.center_lat + IF(n.n IN (1,2),0.000,-0.025),',',
   c.center_lng + IF(n.n IN (1,3),0.000,0.030),' ',c.center_lat + IF(n.n IN (1,2),0.025,0.000),',',
   c.center_lng + IF(n.n IN (1,3),-0.030,0.000),' ',c.center_lat + IF(n.n IN (1,2),0.025,0.000),',',
   c.center_lng + IF(n.n IN (1,3),-0.030,0.000),' ',c.center_lat + IF(n.n IN (1,2),0.000,-0.025),')))'),4326,'axis-order=long-lat'),
 JSON_OBJECT('type','MultiPolygon','properties',JSON_OBJECT('fixture','DEMO-R02')),
 CURRENT_TIMESTAMP(3),JSON_OBJECT('valid',true),'R02 experience fixture',CURRENT_TIMESTAMP(3)
FROM r02_city c JOIN station s ON s.station_code=c.station_code
JOIN delivery_area a ON a.station_id=s.id
JOIN (SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) n
  ON a.area_code=CONCAT('DEMO-R02-',LEFT(c.station_code,3),'-A',n.n)
WHERE NOT EXISTS (SELECT 1 FROM delivery_area_version v WHERE v.delivery_area_id=a.id);

DROP PROCEDURE IF EXISTS seed_r02_experience;
DELIMITER //
CREATE PROCEDURE seed_r02_experience()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE v_station BIGINT UNSIGNED;
  DECLARE v_code VARCHAR(64); DECLARE v_city VARCHAR(100); DECLARE v_province VARCHAR(32); DECLARE v_postal VARCHAR(8);
  DECLARE v_lng DECIMAL(10,6); DECLARE v_lat DECIMAL(10,6); DECLARE i INT; DECLARE v_waybill BIGINT UNSIGNED; DECLARE v_parcel BIGINT UNSIGNED;
  DECLARE city_cur CURSOR FOR SELECT s.id,c.station_code,c.city,c.province,c.postal,c.center_lng,c.center_lat FROM r02_city c JOIN station s ON s.station_code=c.station_code;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done=1;
  OPEN city_cur;
  city_loop: LOOP
    FETCH city_cur INTO v_station,v_code,v_city,v_province,v_postal,v_lng,v_lat;
    IF done=1 THEN LEAVE city_loop; END IF;
    SET i=1;
    parcel_loop: WHILE i<=72 DO
      INSERT IGNORE INTO waybill(partner_id,external_waybill_no,recipient_name,recipient_phone,address_line1,city,province,postal_code,country_code,service_code,routing_status,resolved_station_id,routing_reason_code,routed_at,status)
      SELECT p.id,CONCAT('DEMO-R02-',LEFT(v_code,3),'-',LPAD(i,4,'0')),
       CONCAT(ELT(1+MOD(i-1,12),'Emma','Liam','Olivia','Noah','Ava','William','Sophia','James','Mia','Lucas','Charlotte','Benjamin'),' ',ELT(1+MOD(i*7,10),'Martin','Lee','Patel','Wilson','Chen','Brown','Roy','Taylor','Singh','Garcia')),
       CONCAT('+1-902-555-',LPAD(i,4,'0')),CONCAT(100+i,' ',ELT(1+MOD(i,8),'Barrington St','Spring Garden Rd','Queen St','King St','Robson St','Granville St','Dresden Row','Water St')),
       v_city,v_province,CONCAT(v_postal,' ',ELT(1+MOD(i,6),'1A1','2B2','3C3','4E4','5G5','6H6')),'CA',IF(MOD(i,9)=0,'EXPRESS','STANDARD'),'ROUTED',v_station,'DEMO_STATION',CURRENT_TIMESTAMP(3),'ACTIVE'
      FROM upstream_partner p WHERE p.partner_code='DEMO-R02-UPSTREAM';
      SELECT w.id INTO v_waybill FROM waybill w JOIN upstream_partner p ON p.id=w.partner_id WHERE p.partner_code='DEMO-R02-UPSTREAM' AND w.external_waybill_no=CONCAT('DEMO-R02-',LEFT(v_code,3),'-',LPAD(i,4,'0'));
      INSERT IGNORE INTO parcel(waybill_id,tracking_no,current_station_id,status,current_custody_type,route_code,promised_date)
      VALUES(v_waybill,CONCAT('DEMO-R02-',LEFT(v_code,3),'-',LPAD(i,5,'0')),v_station,IF(MOD(i,4)=0,'AT_STATION','RECEIVED'),IF(MOD(i,4)=0,'STATION','UPSTREAM'),CONCAT('R',1+MOD(i-1,4)),CURRENT_DATE);
      SELECT id INTO v_parcel FROM parcel WHERE tracking_no=CONCAT('DEMO-R02-',LEFT(v_code,3),'-',LPAD(i,5,'0'));
      IF MOD(i,24)<>0 THEN
        INSERT INTO waybill_geocode(waybill_id,delivery_point,provider_code,precision_code,confidence,normalized_address,geocoded_at)
        VALUES(v_waybill,ST_GeomFromText(CONCAT('POINT(',v_lng + IF(MOD(i,24)=23,0.060,-0.028 + MOD(i*13,57)/1000),' ',v_lat -0.023 + MOD(i*17,47)/1000,')'),4326,'axis-order=long-lat'),'DEMO_GEOCODER','ROOFTOP',0.9700,'R02 normalized demo address',CURRENT_TIMESTAMP(3))
        ON DUPLICATE KEY UPDATE confidence=VALUES(confidence),geocoded_at=VALUES(geocoded_at);
        IF MOD(i,24)<>23 AND NOT EXISTS(SELECT 1 FROM parcel_area_assignment WHERE parcel_id=v_parcel AND ended_at IS NULL) THEN
          INSERT INTO parcel_area_assignment(parcel_id,delivery_area_version_id,assignment_source,assignment_reason)
          SELECT v_parcel,av.id,'GEO_POLYGON','R02 fixture spatial match'
          FROM delivery_area_version av JOIN delivery_area a ON a.id=av.delivery_area_id
          JOIN waybill_geocode g ON g.waybill_id=v_waybill
          WHERE a.station_id=v_station AND av.status='PUBLISHED' AND ST_Intersects(av.boundary,g.delivery_point) LIMIT 1;
        END IF;
      END IF;
      SET i=i+1;
    END WHILE;
  END LOOP;
  CLOSE city_cur;
END//
DELIMITER ;
CALL seed_r02_experience();
DROP PROCEDURE seed_r02_experience;
DROP TEMPORARY TABLE r02_city;

SELECT s.station_code,COUNT(DISTINCT d.id) demo_drivers,COUNT(DISTINCT p.id) demo_parcels,
       COUNT(DISTINCT g.waybill_id) geocoded,COUNT(DISTINCT paa.parcel_id) area_matched
FROM station s LEFT JOIN driver d ON d.home_station_id=s.id AND d.credential_id LIKE 'demo.%'
LEFT JOIN parcel p ON p.current_station_id=s.id AND p.tracking_no LIKE 'DEMO-R02-%'
LEFT JOIN waybill_geocode g ON g.waybill_id=p.waybill_id
LEFT JOIN parcel_area_assignment paa ON paa.parcel_id=p.id AND paa.ended_at IS NULL
WHERE s.station_code IN ('YHZ-01','YYZ-01','YVR-01') GROUP BY s.id,s.station_code ORDER BY s.station_code;
