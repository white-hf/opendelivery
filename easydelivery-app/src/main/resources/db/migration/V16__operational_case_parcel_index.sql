-- V16__operational_case_parcel_index.sql
-- Add composite index on operational_case(parcel_id, status) if not existing to optimize dispatch planning case checks

ALTER TABLE operational_case
    ADD KEY idx_case_parcel_status (parcel_id, status);
