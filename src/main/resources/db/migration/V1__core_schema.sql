CREATE TABLE region (
    code VARCHAR(12) PRIMARY KEY,
    parent_code VARCHAR(12),
    name VARCHAR(80) NOT NULL,
    level TINYINT NOT NULL,
    CONSTRAINT fk_region_parent FOREIGN KEY (parent_code) REFERENCES region(code)
);
CREATE INDEX idx_region_parent ON region(parent_code);

CREATE TABLE org_unit (
    id BIGINT PRIMARY KEY,
    parent_id BIGINT,
    org_type VARCHAR(20) NOT NULL,
    region_code VARCHAR(12) NOT NULL,
    name VARCHAR(120) NOT NULL,
    enabled BOOLEAN NOT NULL,
    CONSTRAINT fk_org_parent FOREIGN KEY (parent_id) REFERENCES org_unit(id),
    CONSTRAINT fk_org_region FOREIGN KEY (region_code) REFERENCES region(code)
);
CREATE INDEX idx_org_region ON org_unit(region_code);

CREATE TABLE user_account (
    id BIGINT PRIMARY KEY,
    org_id BIGINT NOT NULL,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT fk_user_org FOREIGN KEY (org_id) REFERENCES org_unit(id)
);

CREATE TABLE `role` (
    id BIGINT PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL
);

CREATE TABLE user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES `role`(id)
);
