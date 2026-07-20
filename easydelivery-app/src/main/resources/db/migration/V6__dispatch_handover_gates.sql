ALTER TABLE scan_session
    ADD COLUMN active_slot TINYINT GENERATED ALWAYS AS
      (CASE WHEN status IN ('OPEN','SUBMITTED') THEN 1 ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_scan_active_task_type (task_id, session_type, active_slot);

CREATE INDEX idx_dispatch_candidate ON parcel(current_station_id,status,current_custody_type,updated_at);

