ALTER TABLE national_batch MODIFY COLUMN payload_json LONGTEXT NULL;
ALTER TABLE national_batch MODIFY COLUMN mode VARCHAR(20) NOT NULL;
ALTER TABLE national_batch ADD COLUMN payload_xml LONGTEXT;
ALTER TABLE national_batch ADD COLUMN payload_format VARCHAR(8) NOT NULL DEFAULT 'JSON';
ALTER TABLE national_batch ADD COLUMN schema_version VARCHAR(8) NOT NULL DEFAULT 'demo-1';
ALTER TABLE national_batch ADD COLUMN real_count INT NOT NULL DEFAULT 0;
ALTER TABLE national_batch ADD COLUMN proxy_count INT NOT NULL DEFAULT 0;
ALTER TABLE national_batch ADD COLUMN chunk_count INT NOT NULL DEFAULT 0;
UPDATE national_batch SET real_count=enterprise_count WHERE schema_version='demo-1';

CREATE TABLE national_proxy_set (
    province_org_id BIGINT NOT NULL,
    period_code VARCHAR(32) NOT NULL,
    current_version INT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (province_org_id,period_code)
);
CREATE TABLE national_proxy_item (
    enterprise_org_id BIGINT NOT NULL,
    period_code VARCHAR(32) NOT NULL,
    province_org_id BIGINT NOT NULL,
    organization_code VARCHAR(9) NOT NULL,
    enterprise_name VARCHAR(120) NOT NULL,
    region_code VARCHAR(12) NOT NULL,
    nature_code VARCHAR(64) NOT NULL,
    industry_code VARCHAR(64) NOT NULL,
    source_period_code VARCHAR(32) NOT NULL,
    source_report_id BIGINT NOT NULL,
    source_submission_version INT NOT NULL,
    source_current_version INT NOT NULL,
    employment_count INT NOT NULL,
    generated_by BIGINT NOT NULL,
    generated_at DATETIME(3) NOT NULL,
    batch_id VARCHAR(48),
    PRIMARY KEY (enterprise_org_id,period_code),
    CONSTRAINT fk_proxy_org FOREIGN KEY (enterprise_org_id) REFERENCES org_unit(id),
    CONSTRAINT fk_proxy_period FOREIGN KEY (period_code) REFERENCES survey_period(code),
    CONSTRAINT fk_proxy_source FOREIGN KEY (source_report_id) REFERENCES enterprise_report(id),
    CONSTRAINT fk_proxy_batch FOREIGN KEY (batch_id) REFERENCES national_batch(batch_id)
);
CREATE INDEX idx_proxy_province_period ON national_proxy_item(province_org_id,period_code);

CREATE TABLE national_chunk_out (
    batch_id VARCHAR(48) NOT NULL,
    chunk_index INT NOT NULL,
    chunk_sha256 CHAR(64) NOT NULL,
    byte_count INT NOT NULL,
    broker_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    sent_at DATETIME(3),
    PRIMARY KEY (batch_id,chunk_index),
    CONSTRAINT fk_chunk_out_batch FOREIGN KEY (batch_id) REFERENCES national_batch(batch_id)
);
CREATE TABLE national_chunk_in (
    batch_id VARCHAR(48) NOT NULL,
    chunk_index INT NOT NULL,
    chunk_count INT NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    chunk_sha256 CHAR(64) NOT NULL,
    payload_bytes BLOB NOT NULL,
    received_at DATETIME(3) NOT NULL,
    PRIMARY KEY (batch_id,chunk_index),
    CONSTRAINT fk_chunk_in_batch FOREIGN KEY (batch_id) REFERENCES national_batch(batch_id)
);
CREATE TABLE national_mock_receipt (
    batch_id VARCHAR(48) PRIMARY KEY,
    receipt_id VARCHAR(80) NOT NULL,
    received_at DATETIME(3) NOT NULL,
    enterprise_count INT NOT NULL,
    real_count INT NOT NULL,
    proxy_count INT NOT NULL,
    chunk_count INT NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    CONSTRAINT fk_mock_receipt_batch FOREIGN KEY (batch_id) REFERENCES national_batch(batch_id)
);
