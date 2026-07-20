INSERT INTO upstream_partner (id, partner_code, partner_name, integration_mode, status, timezone)
VALUES (1, 'DEMO_UPSTREAM', 'Demo Upstream Carrier', 'PUSH', 'ACTIVE', 'America/Halifax');

INSERT INTO station (id, station_code, station_name, city, province_code, country_code, timezone, address_line, status)
VALUES
    (1, 'YHZ-01', 'Halifax Last Mile Station', 'HALIFAX', 'NS', 'CA', 'America/Halifax', 'Halifax, NS', 'ACTIVE'),
    (2, 'YYZ-01', 'Toronto Last Mile Station', 'TORONTO', 'ON', 'CA', 'America/Halifax', 'Toronto, ON', 'ACTIVE'),
    (3, 'YVR-01', 'Vancouver Last Mile Station', 'VANCOUVER', 'BC', 'CA', 'America/Halifax', 'Vancouver, BC', 'ACTIVE');

INSERT INTO station_service_area (station_id, country_code, province_code, city_name, postal_prefix, priority)
VALUES
    (1, 'CA', 'NS', 'HALIFAX', NULL, 100),
    (2, 'CA', 'ON', 'TORONTO', NULL, 100),
    (3, 'CA', 'BC', 'VANCOUVER', NULL, 100);

INSERT INTO operator_user (id, username, password_hash, display_name, default_station_id, status)
VALUES
    (201, 'opsadmin', '$2a$10$8QOXb3qUhBVIecy5luJH6emzRfxlrxbCjc2YnUPqeb/RqKQReHH3.', 'Operations Admin', 1, 'ACTIVE'),
    (202, 'inbound.yhz', '$2a$10$8QOXb3qUhBVIecy5luJH6emzRfxlrxbCjc2YnUPqeb/RqKQReHH3.', 'Halifax Inbound', 1, 'ACTIVE'),
    (203, 'dispatch.yhz', '$2a$10$8QOXb3qUhBVIecy5luJH6emzRfxlrxbCjc2YnUPqeb/RqKQReHH3.', 'Halifax Dispatcher', 1, 'ACTIVE');

INSERT INTO operator_user_role (user_id, role_id)
SELECT 201, id FROM operator_role WHERE role_code='ADMIN';
INSERT INTO operator_user_role (user_id, role_id)
SELECT 202, id FROM operator_role WHERE role_code='INBOUND';
INSERT INTO operator_user_role (user_id, role_id)
SELECT 203, id FROM operator_role WHERE role_code='DISPATCHER';

INSERT INTO driver (id, home_station_id, credential_id, password_hash, driver_name, phone, status)
VALUES
    (101, 1, 'driver123', '$2a$10$N9pprz7VCiwZvaGRp/7sQOh3qRIcIfYXt5wJYZTCQmWpRbiOgK2nm', 'Alex Driver', '604-555-0199', 'ACTIVE'),
    (102, 1, 'test', '$2a$10$8QOXb3qUhBVIecy5luJH6emzRfxlrxbCjc2YnUPqeb/RqKQReHH3.', 'Test Driver', '604-555-0102', 'ACTIVE');

INSERT INTO waybill (
    id, partner_id, external_waybill_no, recipient_name, recipient_phone,
    address_line1, city, province, postal_code, country_code, service_code,
    routing_status, resolved_station_id, routing_reason_code, routed_at, status
) VALUES
    (10001, 1, 'SN10001', 'John Doe', '902-555-0199', '123 Barrington St', 'Halifax', 'NS', 'B3J 1Z2', 'CA', 'REGULAR', 'ROUTED', 1, 'DEVELOPMENT_SEED', CURRENT_TIMESTAMP(3), 'ACTIVE'),
    (10002, 1, 'SN10002', 'Alice Smith', '902-555-0199', '125 Barrington St', 'Halifax', 'NS', 'B3J 1Z2', 'CA', 'REGULAR', 'ROUTED', 1, 'DEVELOPMENT_SEED', CURRENT_TIMESTAMP(3), 'ACTIVE'),
    (10003, 1, 'SN10003', 'Bob Chen', '902-555-0199', '1800 Argyle St', 'Halifax', 'NS', 'B3J 3N8', 'CA', 'REGULAR', 'ROUTED', 1, 'DEVELOPMENT_SEED', CURRENT_TIMESTAMP(3), 'ACTIVE'),
    (10004, 1, 'SN10004', 'David Wong', '902-555-0199', '1810 Argyle St', 'Halifax', 'NS', 'B3J 3N8', 'CA', 'REGULAR', 'ROUTED', 1, 'DEVELOPMENT_SEED', CURRENT_TIMESTAMP(3), 'ACTIVE'),
    (10005, 1, 'SN10005', 'Emma Wilson', '902-555-0199', '1650 Hollis St', 'Halifax', 'NS', 'B3J 1V7', 'CA', 'REGULAR', 'ROUTED', 1, 'DEVELOPMENT_SEED', CURRENT_TIMESTAMP(3), 'ACTIVE'),
    (10006, 1, 'SN10006', 'George Martin', '902-555-0199', '1660 Hollis St', 'Halifax', 'NS', 'B3J 1V7', 'CA', 'REGULAR', 'ROUTED', 1, 'DEVELOPMENT_SEED', CURRENT_TIMESTAMP(3), 'ACTIVE');

INSERT INTO parcel (
    id, waybill_id, tracking_no, current_station_id, status, current_custody_type,
    current_custody_id, current_location_code, route_code, promised_date
) VALUES
    (10001, 10001, 'BAUNI000300014438615', 1, 'ASSIGNED', 'STATION', 1, 'SORT-12', '12', CURRENT_DATE),
    (10002, 10002, 'BAUNI000300014438616', 1, 'ASSIGNED', 'STATION', 1, 'SORT-12', '12', CURRENT_DATE),
    (10003, 10003, 'BAUNI000300014438617', 1, 'OUT_FOR_DELIVERY', 'DRIVER', 101, 'VEHICLE-101', '12', CURRENT_DATE),
    (10004, 10004, 'BAUNI000300014438618', 1, 'OUT_FOR_DELIVERY', 'DRIVER', 101, 'VEHICLE-101', '12', CURRENT_DATE),
    (10005, 10005, 'BAUNI000300014438619', 1, 'OUT_FOR_DELIVERY', 'DRIVER', 101, 'VEHICLE-101', '12', CURRENT_DATE),
    (10006, 10006, 'BAUNI000300014438620', 1, 'ASSIGNED', 'STATION', 1, 'SORT-12', '12', CURRENT_DATE);

INSERT INTO dispatch_wave (id, station_id, wave_code, service_date, route_code, status, published_at)
VALUES (1001, 1, 'DEMO-WAVE-12', CURRENT_DATE, '12', 'IN_PROGRESS', CURRENT_TIMESTAMP(3));

INSERT INTO driver_task (id, wave_id, driver_id, station_id, task_code, service_date, status, accepted_at, started_at)
VALUES (1001, 1001, 101, 1, 'DEMO-TASK-101', CURRENT_DATE, 'IN_PROGRESS', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT INTO driver_task_item (task_id, parcel_id, stop_sequence, item_status)
VALUES
    (1001, 10001, 1, 'ASSIGNED'),
    (1001, 10002, 2, 'ASSIGNED'),
    (1001, 10003, 3, 'OUT_FOR_DELIVERY'),
    (1001, 10004, 4, 'OUT_FOR_DELIVERY'),
    (1001, 10005, 5, 'OUT_FOR_DELIVERY'),
    (1001, 10006, 6, 'ASSIGNED');
