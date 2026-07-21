ALTER TABLE station
    ADD COLUMN default_capacity INT UNSIGNED NOT NULL DEFAULT 200 AFTER address_line;
