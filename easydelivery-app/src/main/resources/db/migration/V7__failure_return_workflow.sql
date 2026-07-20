CREATE TABLE delivery_failure_reason (
    reason_code VARCHAR(64) NOT NULL,
    reason_name VARCHAR(160) NOT NULL,
    requires_photo TINYINT(1) NOT NULL DEFAULT 0,
    requires_note TINYINT(1) NOT NULL DEFAULT 0,
    next_action VARCHAR(32) NOT NULL,
    max_attempts SMALLINT UNSIGNED NOT NULL DEFAULT 3,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (reason_code),
    CONSTRAINT ck_failure_next_action CHECK (next_action IN ('REDISPATCH','RETURN_STATION','RETURN_UPSTREAM','ADDRESS_CASE')),
    CONSTRAINT ck_failure_reason_status CHECK (status IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB;

ALTER TABLE delivery_attempt
    ADD COLUMN failure_note VARCHAR(1000) NULL AFTER failure_reason_code,
    ADD COLUMN next_action VARCHAR(32) NULL AFTER failure_note;

ALTER TABLE scan_session
    ADD COLUMN resolution_code VARCHAR(64) NULL AFTER reviewed_at,
    ADD COLUMN resolution_note VARCHAR(1000) NULL AFTER resolution_code;

INSERT INTO delivery_failure_reason(reason_code,reason_name,requires_photo,requires_note,next_action,max_attempts) VALUES
 ('NO_ANSWER','No answer',0,0,'REDISPATCH',3),
 ('RECIPIENT_REFUSED','Recipient refused',0,1,'RETURN_UPSTREAM',1),
 ('ADDRESS_INVALID','Address invalid',1,1,'ADDRESS_CASE',1),
 ('ACCESS_BLOCKED','Access blocked',1,1,'REDISPATCH',3),
 ('DAMAGED','Parcel damaged',1,1,'RETURN_STATION',1);
