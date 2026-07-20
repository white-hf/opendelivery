ALTER TABLE station
    ADD COLUMN default_locale VARCHAR(16) NOT NULL DEFAULT 'en-CA' AFTER timezone,
    ADD CONSTRAINT ck_station_default_locale CHECK (default_locale IN ('en-CA','fr-CA','zh-CN'));

ALTER TABLE operator_user
    ADD COLUMN preferred_locale VARCHAR(16) NOT NULL DEFAULT 'en-CA' AFTER display_name,
    ADD CONSTRAINT ck_operator_preferred_locale CHECK (preferred_locale IN ('en-CA','fr-CA','zh-CN'));

ALTER TABLE driver
    ADD COLUMN preferred_locale VARCHAR(16) NOT NULL DEFAULT 'en-CA' AFTER driver_name,
    ADD CONSTRAINT ck_driver_preferred_locale CHECK (preferred_locale IN ('en-CA','fr-CA','zh-CN'));
