CREATE TABLE national_batch (
    batch_id VARCHAR(48) PRIMARY KEY,
    province_org_id BIGINT NOT NULL,
    period_code VARCHAR(32) NOT NULL,
    mode VARCHAR(8) NOT NULL,
    status VARCHAR(20) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    enterprise_count INT NOT NULL,
    baseline_total BIGINT NOT NULL,
    period_total BIGINT NOT NULL,
    net_change BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    last_sent_at DATETIME(3),
    last_error VARCHAR(500),
    receipt_id VARCHAR(80),
    receipt_at DATETIME(3),
    CONSTRAINT uk_national_period UNIQUE (province_org_id, period_code),
    CONSTRAINT fk_national_province FOREIGN KEY (province_org_id) REFERENCES org_unit(id),
    CONSTRAINT fk_national_period FOREIGN KEY (period_code) REFERENCES survey_period(code)
);

CREATE TABLE national_batch_item (
    batch_id VARCHAR(48) NOT NULL,
    report_id BIGINT NOT NULL PRIMARY KEY,
    submission_version INT NOT NULL,
    current_version INT NOT NULL,
    baseline_employment_count INT NOT NULL,
    period_employment_count INT NOT NULL,
    CONSTRAINT fk_national_item_batch FOREIGN KEY (batch_id) REFERENCES national_batch(batch_id),
    CONSTRAINT fk_national_item_report FOREIGN KEY (report_id) REFERENCES enterprise_report(id)
);
CREATE INDEX idx_national_item_batch ON national_batch_item(batch_id);

CREATE TABLE national_send_attempt (
    batch_id VARCHAR(48) NOT NULL,
    attempt_no INT NOT NULL,
    started_at DATETIME(3) NOT NULL,
    finished_at DATETIME(3),
    result VARCHAR(12) NOT NULL,
    error_summary VARCHAR(500),
    mock_receipt_id VARCHAR(80),
    PRIMARY KEY (batch_id, attempt_no),
    CONSTRAINT fk_national_attempt_batch FOREIGN KEY (batch_id) REFERENCES national_batch(batch_id)
);
