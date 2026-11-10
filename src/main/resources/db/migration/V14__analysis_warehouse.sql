CREATE TABLE dw_dim_period (
    period_code VARCHAR(32) PRIMARY KEY,
    province_org_id BIGINT NOT NULL,
    label VARCHAR(80) NOT NULL,
    start_at DATETIME(3) NOT NULL,
    end_exclusive_at DATETIME(3) NOT NULL
);
CREATE TABLE dw_dim_region (
    region_code VARCHAR(12) PRIMARY KEY,
    parent_code VARCHAR(12),
    name VARCHAR(80) NOT NULL,
    level TINYINT NOT NULL
);
CREATE TABLE dw_dim_enterprise (
    enterprise_org_id BIGINT PRIMARY KEY,
    province_org_id BIGINT NOT NULL,
    city_org_id BIGINT NOT NULL,
    region_code VARCHAR(12) NOT NULL,
    name VARCHAR(120) NOT NULL
);
CREATE TABLE dw_dim_nature (
    code VARCHAR(64) PRIMARY KEY,
    parent_code VARCHAR(64),
    name VARCHAR(120) NOT NULL
);
CREATE TABLE dw_dim_industry (
    code VARCHAR(64) PRIMARY KEY,
    parent_code VARCHAR(64),
    name VARCHAR(120) NOT NULL
);
CREATE TABLE dw_fact_employment_month (
    enterprise_org_id BIGINT NOT NULL,
    period_code VARCHAR(32) NOT NULL,
    province_org_id BIGINT NOT NULL,
    city_org_id BIGINT NOT NULL,
    report_id BIGINT NOT NULL,
    submission_version INT NOT NULL,
    current_version INT NOT NULL,
    snapshot_filing_id BIGINT,
    current_filing_id BIGINT,
    organization_code VARCHAR(9),
    enterprise_name VARCHAR(120) NOT NULL,
    region_code VARCHAR(12) NOT NULL,
    nature_parent_code VARCHAR(64),
    nature_code VARCHAR(64),
    industry_parent_code VARCHAR(64),
    industry_code VARCHAR(64),
    baseline_source_period_code VARCHAR(32),
    baseline_employment_count INT NOT NULL,
    period_employment_count INT NOT NULL,
    net_change INT NOT NULL,
    reduction_count INT NOT NULL,
    primary_reason_code VARCHAR(64),
    primary_reason_detail VARCHAR(500),
    correction_count INT NOT NULL,
    valid BOOLEAN NOT NULL,
    synced_at DATETIME(3) NOT NULL,
    PRIMARY KEY (enterprise_org_id, period_code)
);
CREATE INDEX idx_dw_fact_province_period ON dw_fact_employment_month(province_org_id, period_code);
CREATE INDEX idx_dw_fact_region ON dw_fact_employment_month(region_code,period_code);
CREATE TABLE dw_sync_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    started_at DATETIME(3) NOT NULL,
    finished_at DATETIME(3) NOT NULL,
    result VARCHAR(16) NOT NULL,
    row_count INT NOT NULL,
    error_summary VARCHAR(500)
);
