CREATE TABLE operator_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    default_station_id BIGINT UNSIGNED NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_operator_username (username),
    KEY idx_operator_station_status (default_station_id, status),
    CONSTRAINT fk_operator_station FOREIGN KEY (default_station_id) REFERENCES station (id),
    CONSTRAINT ck_operator_status CHECK (status IN ('ACTIVE','SUSPENDED','INACTIVE'))
) ENGINE=InnoDB;

CREATE TABLE operator_role (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    role_code VARCHAR(40) NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_operator_role_code (role_code)
) ENGINE=InnoDB;

CREATE TABLE operator_user_role (
    user_id BIGINT UNSIGNED NOT NULL,
    role_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES operator_user (id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES operator_role (id)
) ENGINE=InnoDB;

CREATE TABLE operator_session (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    access_token_hash CHAR(64) NOT NULL,
    refresh_token_hash CHAR(64) NOT NULL,
    access_expires_at DATETIME(3) NOT NULL,
    refresh_expires_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_operator_access_token (access_token_hash),
    UNIQUE KEY uk_operator_refresh_token (refresh_token_hash),
    KEY idx_operator_session_user (user_id, revoked_at),
    CONSTRAINT fk_operator_session_user FOREIGN KEY (user_id) REFERENCES operator_user (id)
) ENGINE=InnoDB;

CREATE TABLE operation_audit_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    operator_user_id BIGINT UNSIGNED NULL,
    station_id BIGINT UNSIGNED NULL,
    action_code VARCHAR(80) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NULL,
    outcome VARCHAR(16) NOT NULL,
    request_id VARCHAR(100) NULL,
    detail_json JSON NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_audit_user_time (operator_user_id, created_at),
    KEY idx_audit_station_time (station_id, created_at),
    KEY idx_audit_resource (resource_type, resource_id, created_at),
    CONSTRAINT fk_audit_operator FOREIGN KEY (operator_user_id) REFERENCES operator_user (id),
    CONSTRAINT fk_audit_station FOREIGN KEY (station_id) REFERENCES station (id),
    CONSTRAINT ck_audit_outcome CHECK (outcome IN ('SUCCESS','DENIED','FAILED'))
) ENGINE=InnoDB;

INSERT INTO operator_role (role_code, role_name) VALUES
 ('ADMIN','Administrator'), ('INBOUND','Inbound Operator'),
 ('DISPATCHER','Dispatcher'), ('SUPERVISOR','Station Supervisor');

