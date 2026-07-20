INSERT INTO upstream_partner (id, partner_code, partner_name, integration_mode, status, timezone)
VALUES (1, 'DEMO_UPSTREAM', 'Demo Upstream Carrier', 'PUSH', 'ACTIVE', 'America/Halifax');

INSERT INTO station (id, station_code, station_name, timezone, address_line, status)
VALUES (1, 'YHZ-01', 'Halifax Last Mile Station', 'America/Halifax', 'Halifax, NS', 'ACTIVE');

INSERT INTO driver (id, home_station_id, credential_id, password_hash, driver_name, phone, status)
VALUES
    (101, 1, 'driver123', '$2a$10$N9pprz7VCiwZvaGRp/7sQOh3qRIcIfYXt5wJYZTCQmWpRbiOgK2nm', 'Alex Driver', '604-555-0199', 'ACTIVE'),
    (102, 1, 'test', '$2a$10$8QOXb3qUhBVIecy5luJH6emzRfxlrxbCjc2YnUPqeb/RqKQReHH3.', 'Test Driver', '604-555-0102', 'ACTIVE');

INSERT INTO waybill (
    id, partner_id, external_waybill_no, recipient_name, recipient_phone,
    address_line1, city, province, postal_code, country_code, service_code, status
) VALUES
    (10001, 1, 'SN10001', 'John Doe', '604-555-0199', '123 Main St', 'Vancouver', 'BC', 'V6B 1A1', 'CA', 'REGULAR', 'ACTIVE'),
    (10002, 1, 'SN10002', 'Alice Smith', '604-555-0199', '125 Main St', 'Vancouver', 'BC', 'V6B 1A1', 'CA', 'REGULAR', 'ACTIVE'),
    (10003, 1, 'SN10003', 'Bob Chen', '604-555-0199', '888 Kingsway', 'Vancouver', 'BC', 'V5V 3C3', 'CA', 'REGULAR', 'ACTIVE'),
    (10004, 1, 'SN10004', 'David Wong', '604-555-0199', '890 Kingsway', 'Vancouver', 'BC', 'V5V 3C3', 'CA', 'REGULAR', 'ACTIVE'),
    (10005, 1, 'SN10005', 'Emma Wilson', '604-555-0199', '1055 W Georgia St', 'Vancouver', 'BC', 'V6E 3P3', 'CA', 'REGULAR', 'ACTIVE'),
    (10006, 1, 'SN10006', 'George Martin', '604-555-0199', '2000 Simcoe St', 'Vancouver', 'BC', 'V6E 3P4', 'CA', 'REGULAR', 'ACTIVE');

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
