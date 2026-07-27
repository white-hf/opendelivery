-- Persist the optional inbound trip selected during dispatch-wave planning.
ALTER TABLE dispatch_wave
    ADD COLUMN arrival_trip_id BIGINT UNSIGNED NULL AFTER service_date,
    ADD KEY idx_wave_arrival_trip (arrival_trip_id),
    ADD CONSTRAINT fk_wave_arrival_trip FOREIGN KEY (arrival_trip_id) REFERENCES arrival_trip(id);
