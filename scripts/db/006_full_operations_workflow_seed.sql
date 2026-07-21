-- Full Operations Workflow Seed Data (Non-production demo/testing).
-- Apply after Flyway migrations and 003_three_station_pilot_seed.sql.

-- 1. Ensure test station operators exist for both 'test' / 'test' and 'password123' logins
INSERT INTO operator_user (username, password_hash, display_name, default_station_id, status)
SELECT 'test', '$2a$10$8QOXb3qUhBVIecy5luJH6emzRfxlrxbCjc2YnUPqeb/RqKQReHH3.', 'Test Lead Operator', s.id, 'ACTIVE'
FROM station s WHERE s.station_code = 'YHZ-01'
ON DUPLICATE KEY UPDATE password_hash = '$2a$10$8QOXb3qUhBVIecy5luJH6emzRfxlrxbCjc2YnUPqeb/RqKQReHH3.', status = 'ACTIVE';

INSERT INTO operator_user (username, password_hash, display_name, default_station_id, status)
SELECT 'opsadmin', '$2a$10$x98JYz3ZgYUzuFZbhj1u5.AEbJieTEyYuChW3/dg4bO7iQ8n2pO02', 'Operations Admin', s.id, 'ACTIVE'
FROM station s WHERE s.station_code = 'YHZ-01'
ON DUPLICATE KEY UPDATE password_hash = '$2a$10$x98JYz3ZgYUzuFZbhj1u5.AEbJieTEyYuChW3/dg4bO7iQ8n2pO02', status = 'ACTIVE';

INSERT INTO operator_user (username, password_hash, display_name, default_station_id, status)
SELECT 'inbound.yhz', '$2a$10$x98JYz3ZgYUzuFZbhj1u5.AEbJieTEyYuChW3/dg4bO7iQ8n2pO02', 'Halifax Inbound', s.id, 'ACTIVE'
FROM station s WHERE s.station_code = 'YHZ-01'
ON DUPLICATE KEY UPDATE password_hash = '$2a$10$x98JYz3ZgYUzuFZbhj1u5.AEbJieTEyYuChW3/dg4bO7iQ8n2pO02', status = 'ACTIVE';

INSERT INTO operator_user (username, password_hash, display_name, default_station_id, status)
SELECT 'dispatch.yhz', '$2a$10$x98JYz3ZgYUzuFZbhj1u5.AEbJieTEyYuChW3/dg4bO7iQ8n2pO02', 'Halifax Dispatcher', s.id, 'ACTIVE'
FROM station s WHERE s.station_code = 'YHZ-01'
ON DUPLICATE KEY UPDATE password_hash = '$2a$10$x98JYz3ZgYUzuFZbhj1u5.AEbJieTEyYuChW3/dg4bO7iQ8n2pO02', status = 'ACTIVE';

INSERT IGNORE INTO operator_user_role (user_id, role_id)
SELECT u.id, r.id FROM operator_user u JOIN operator_role r ON r.role_code IN ('INBOUND', 'DISPATCHER', 'STATION_MANAGER')
WHERE u.username IN ('test', 'opsadmin', 'inbound.yhz', 'dispatch.yhz');

INSERT INTO driver (home_station_id, credential_id, password_hash, driver_name, phone, status)
SELECT s.id, seed.credential_id, '$2a$10$x98JYz3ZgYUzuFZbhj1u5.AEbJieTEyYuChW3/dg4bO7iQ8n2pO02', seed.driver_name, seed.phone, 'ACTIVE'
FROM station s CROSS JOIN (
    SELECT 'driver123' AS credential_id, 'John Driver' AS driver_name, '902-555-0101' AS phone
    UNION ALL SELECT 'driver101', 'Alice Smith', '902-555-0102'
    UNION ALL SELECT 'driver102', 'Bob Jones', '902-555-0103'
) seed WHERE s.station_code = 'YHZ-01'
ON DUPLICATE KEY UPDATE password_hash = '$2a$10$x98JYz3ZgYUzuFZbhj1u5.AEbJieTEyYuChW3/dg4bO7iQ8n2pO02', status = 'ACTIVE';

-- 2. Upstream partner & waybill / manifest data
INSERT INTO upstream_partner (partner_code, partner_name, integration_mode, status)
VALUES ('DEMO_UPSTREAM', 'Demo Logistics Upstream Partner', 'PUSH', 'ACTIVE')
ON DUPLICATE KEY UPDATE status = 'ACTIVE';

INSERT INTO waybill (partner_id, external_waybill_no, recipient_name, recipient_phone, address_line1, city, province, postal_code, created_at)
SELECT p.id, seed.waybill_no, seed.name, seed.phone, seed.addr, 'Halifax', 'NS', seed.post, CURRENT_TIMESTAMP(3)
FROM upstream_partner p CROSS JOIN (
    SELECT 'WAYBILL-DEMO-001' AS waybill_no, 'Alice Johnson' AS name, '902-111-2233' AS phone, '100 Barrington St' AS addr, 'B3J 1Y2' AS post
    UNION ALL SELECT 'WAYBILL-DEMO-002', 'Bob Builder', '902-222-3344', '200 Spring Garden Rd', 'B3H 1Y4'
    UNION ALL SELECT 'WAYBILL-DEMO-003', 'Charlie Brown', '902-333-4455', '300 Gottingen St', 'B3K 3B7'
    UNION ALL SELECT 'WAYBILL-DEMO-004', 'David Miller', '902-444-5566', '400 Quinpool Rd', 'B3L 1A3'
) seed WHERE p.partner_code = 'DEMO_UPSTREAM'
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO inbound_manifest (partner_id, station_id, external_manifest_no, status, expected_count, received_count, discrepancy_count, created_at)
SELECT p.id, s.id, 'EXT-MF-001', 'RECEIVING', 4, 4, 0, CURRENT_TIMESTAMP(3)
FROM upstream_partner p JOIN station s ON s.station_code = 'YHZ-01'
WHERE p.partner_code = 'DEMO_UPSTREAM'
ON DUPLICATE KEY UPDATE status = VALUES(status);

-- 3. Demo Parcels matched by waybill_no
INSERT INTO parcel (waybill_id, tracking_no, status, current_station_id, current_custody_type, current_custody_id, promised_date, created_at)
SELECT w.id, seed.tracking_no, seed.status, s.id, seed.custody_type, s.id, CURRENT_DATE, CURRENT_TIMESTAMP(3)
FROM waybill w JOIN station s ON s.station_code = 'YHZ-01'
CROSS JOIN (
    SELECT 'WAYBILL-DEMO-001' AS waybill_no, 'TRK-DEMO-READY-01' AS tracking_no, 'READY_FOR_DISPATCH' AS status, 'STATION' AS custody_type
    UNION ALL SELECT 'WAYBILL-DEMO-002', 'TRK-DEMO-ASSIGNED-01', 'ASSIGNED', 'STATION'
    UNION ALL SELECT 'WAYBILL-DEMO-003', 'TRK-DEMO-FAILED-01', 'DELIVERY_FAILED', 'DRIVER'
    UNION ALL SELECT 'WAYBILL-DEMO-004', 'TRK-DEMO-DELIVERED-01', 'DELIVERED', 'STATION'
) seed WHERE w.external_waybill_no = seed.waybill_no
ON DUPLICATE KEY UPDATE status = VALUES(status);

-- 4. Dispatch Wave & Driver Tasks for Wave Planning / Scan Supervision / Handover Testing
INSERT INTO dispatch_wave (station_id, wave_code, service_date, status, created_at)
SELECT s.id, 'WAVE-DEMO-001', CURRENT_DATE, 'PUBLISHED', CURRENT_TIMESTAMP(3)
FROM station s WHERE s.station_code = 'YHZ-01'
ON DUPLICATE KEY UPDATE status = 'PUBLISHED';

INSERT INTO driver_task (wave_id, driver_id, station_id, task_code, service_date, status, created_at)
SELECT w.id, d.id, s.id, 'TASK-DEMO-001', CURRENT_DATE, 'IN_PROGRESS', CURRENT_TIMESTAMP(3)
FROM dispatch_wave w JOIN station s ON s.station_code = 'YHZ-01'
JOIN driver d ON d.credential_id = 'driver123'
WHERE w.wave_code = 'WAVE-DEMO-001' AND w.station_id = s.id
ON DUPLICATE KEY UPDATE status = 'IN_PROGRESS';

INSERT INTO driver_task_item (task_id, parcel_id, stop_sequence, item_status, created_at)
SELECT t.id, p.id, seed.seq, seed.item_status, CURRENT_TIMESTAMP(3)
FROM driver_task t CROSS JOIN (
    SELECT 'TRK-DEMO-ASSIGNED-01' AS tracking_no, 1 AS seq, 'ASSIGNED' AS item_status
    UNION ALL SELECT 'TRK-DEMO-FAILED-01', 2, 'FAILED'
    UNION ALL SELECT 'TRK-DEMO-DELIVERED-01', 3, 'DELIVERED'
) seed JOIN parcel p ON p.tracking_no = seed.tracking_no
WHERE t.task_code = 'TASK-DEMO-001'
ON DUPLICATE KEY UPDATE item_status = VALUES(item_status);

-- 5. Scan Session for Handover Approval Workspace Testing
INSERT INTO scan_session (task_id, driver_id, session_type, status, opened_at, submitted_at)
SELECT t.id, t.driver_id, 'LOAD', 'SUBMITTED', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM driver_task t WHERE t.task_code = 'TASK-DEMO-001'
ON DUPLICATE KEY UPDATE status = 'SUBMITTED';

-- 6. Operational Case for Case Center Testing
INSERT INTO operational_case (case_no, case_type, station_id, priority, status, created_at)
SELECT 'CASE-DEMO-001', 'DISCREPANCY', s.id, 'NORMAL', 'OPEN', CURRENT_TIMESTAMP(3)
FROM station s WHERE s.station_code = 'YHZ-01'
ON DUPLICATE KEY UPDATE status = 'OPEN';

-- 7. Outbox Dead Letter Event for Replay Testing
INSERT INTO outbox_event (aggregate_type, aggregate_id, event_type, event_key, partner_id, payload_json, status, attempt_count, last_error, created_at)
SELECT 'PARCEL', p.id, 'DELIVERY_FAILED', 'event-failed-001', pt.id, '{"parcelId": 3, "reason": "FAILED_ATTEMPT"}', 'DEAD_LETTER', 5, 'HTTP 500: Connection timeout from upstream webhook endpoint', CURRENT_TIMESTAMP(3)
FROM parcel p JOIN upstream_partner pt ON pt.partner_code = 'DEMO_UPSTREAM'
WHERE p.tracking_no = 'TRK-DEMO-FAILED-01'
ON DUPLICATE KEY UPDATE status = 'DEAD_LETTER';

-- 8. Daily Reconciliation for Day Close Testing
INSERT INTO daily_reconciliation (station_id, business_date, opening_count, inbound_count, dispatched_count, driver_return_count, delivered_count, variance_count, open_case_count, status, created_at)
SELECT s.id, CURRENT_DATE, 0, 4, 3, 1, 1, 0, 1, 'OPEN', CURRENT_TIMESTAMP(3)
FROM station s WHERE s.station_code = 'YHZ-01'
ON DUPLICATE KEY UPDATE status = 'OPEN';
