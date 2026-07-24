-- Flyway Migration: Add pre-aggregated counter cache columns to scan_session table (valid, unknown, extra)
ALTER TABLE scan_session
    ADD COLUMN valid_count INT UNSIGNED NOT NULL DEFAULT 0 AFTER discrepancy_count,
    ADD COLUMN unknown_count INT UNSIGNED NOT NULL DEFAULT 0 AFTER valid_count,
    ADD COLUMN extra_count INT UNSIGNED NOT NULL DEFAULT 0 AFTER unknown_count;

