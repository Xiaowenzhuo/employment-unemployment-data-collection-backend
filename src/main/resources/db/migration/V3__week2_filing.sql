CREATE TABLE id_allocator (
    entity_name VARCHAR(30) PRIMARY KEY,
    next_id BIGINT NOT NULL
);
INSERT INTO id_allocator SELECT 'org_unit', GREATEST(COALESCE(MAX(id), 0) + 1, 1000) FROM org_unit;
INSERT INTO id_allocator SELECT 'user_account', GREATEST(COALESCE(MAX(id), 0) + 1, 1000) FROM user_account;
INSERT INTO id_allocator VALUES ('enterprise_filing', 1);

CREATE TABLE filing_option (
    code VARCHAR(64) PRIMARY KEY,
    category VARCHAR(20) NOT NULL,
    parent_code VARCHAR(64),
    name VARCHAR(120) NOT NULL,
    demo BOOLEAN NOT NULL,
    CONSTRAINT fk_option_parent FOREIGN KEY (parent_code) REFERENCES filing_option(code)
);
CREATE INDEX idx_option_category_parent ON filing_option(category, parent_code);

INSERT INTO filing_option VALUES ('DEMO_NATURE_1', 'NATURE', NULL, '演示企业性质', true);
INSERT INTO filing_option VALUES ('DEMO_NATURE_1_1', 'NATURE', 'DEMO_NATURE_1', '演示有限责任公司', true);
INSERT INTO filing_option VALUES ('DEMO_INDUSTRY_1', 'INDUSTRY', NULL, '演示行业', true);
INSERT INTO filing_option VALUES ('DEMO_INDUSTRY_1_1', 'INDUSTRY', 'DEMO_INDUSTRY_1', '演示制造业', true);
INSERT INTO filing_option VALUES ('DEMO_ADDR_1', 'ADDRESS', NULL, '演示联系地址一级', true);
INSERT INTO filing_option VALUES ('DEMO_ADDR_1_1', 'ADDRESS', 'DEMO_ADDR_1', '演示联系地址二级', true);

CREATE TABLE survey_period (
    code VARCHAR(32) PRIMARY KEY,
    label VARCHAR(80) NOT NULL,
    start_at DATETIME(3) NOT NULL,
    end_exclusive_at DATETIME(3) NOT NULL,
    demo BOOLEAN NOT NULL
);
INSERT INTO survey_period VALUES ('DEMO_2026_10', '2026年10月（演示）',
    '2026-09-30 16:00:00', '2026-10-31 16:00:00', true);

CREATE TABLE enterprise_filing (
    id BIGINT PRIMARY KEY,
    org_id BIGINT NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL,
    current_version INT NOT NULL,
    organization_code VARCHAR(9) UNIQUE,
    enterprise_name VARCHAR(120),
    nature_parent_code VARCHAR(64),
    nature_code VARCHAR(64),
    industry_parent_code VARCHAR(64),
    industry_code VARCHAR(64),
    main_business VARCHAR(500),
    contact_name VARCHAR(80),
    address_parent_code VARCHAR(64),
    address_code VARCHAR(64),
    postal_code VARCHAR(6),
    contact_phone VARCHAR(20),
    fax VARCHAR(20),
    email VARCHAR(254),
    updated_at DATETIME(3) NOT NULL,
    CONSTRAINT fk_filing_org FOREIGN KEY (org_id) REFERENCES org_unit(id)
);
CREATE INDEX idx_filing_status ON enterprise_filing(status, id);

CREATE TABLE filing_version (
    filing_id BIGINT NOT NULL,
    version INT NOT NULL,
    result VARCHAR(16) NOT NULL,
    organization_code VARCHAR(9) NOT NULL,
    enterprise_name VARCHAR(120) NOT NULL,
    nature_parent_code VARCHAR(64) NOT NULL,
    nature_code VARCHAR(64) NOT NULL,
    industry_parent_code VARCHAR(64) NOT NULL,
    industry_code VARCHAR(64) NOT NULL,
    main_business VARCHAR(500) NOT NULL,
    contact_name VARCHAR(80) NOT NULL,
    address_parent_code VARCHAR(64) NOT NULL,
    address_code VARCHAR(64) NOT NULL,
    postal_code VARCHAR(6) NOT NULL,
    contact_phone VARCHAR(20) NOT NULL,
    fax VARCHAR(20) NOT NULL,
    email VARCHAR(254),
    submitted_at DATETIME(3) NOT NULL,
    submitted_by BIGINT NOT NULL,
    reviewed_at DATETIME(3),
    reviewed_by BIGINT,
    reason VARCHAR(500),
    PRIMARY KEY (filing_id, version),
    CONSTRAINT fk_version_filing FOREIGN KEY (filing_id) REFERENCES enterprise_filing(id),
    CONSTRAINT fk_version_submitter FOREIGN KEY (submitted_by) REFERENCES user_account(id),
    CONSTRAINT fk_version_reviewer FOREIGN KEY (reviewed_by) REFERENCES user_account(id)
);
CREATE INDEX idx_version_review ON filing_version(result, reviewed_at);

CREATE TABLE review_record (
    filing_id BIGINT NOT NULL,
    version INT NOT NULL,
    action VARCHAR(16) NOT NULL,
    reviewer_id BIGINT NOT NULL,
    reviewed_at DATETIME(3) NOT NULL,
    reason VARCHAR(500),
    PRIMARY KEY (filing_id, version),
    CONSTRAINT fk_review_version FOREIGN KEY (filing_id, version) REFERENCES filing_version(filing_id, version),
    CONSTRAINT fk_review_user FOREIGN KEY (reviewer_id) REFERENCES user_account(id)
);

CREATE TABLE audit_event (
    event_id VARCHAR(36) PRIMARY KEY,
    actor_user_id BIGINT NOT NULL,
    action VARCHAR(40) NOT NULL,
    target_org_id BIGINT NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    detail_code VARCHAR(64),
    CONSTRAINT fk_audit_user FOREIGN KEY (actor_user_id) REFERENCES user_account(id),
    CONSTRAINT fk_audit_org FOREIGN KEY (target_org_id) REFERENCES org_unit(id)
);
