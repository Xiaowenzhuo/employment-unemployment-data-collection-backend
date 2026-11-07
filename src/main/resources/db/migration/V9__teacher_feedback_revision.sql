ALTER TABLE enterprise_report ADD COLUMN baseline_source_period_code VARCHAR(32);
ALTER TABLE enterprise_report ADD COLUMN baseline_source_report_id BIGINT;
ALTER TABLE enterprise_report ADD COLUMN baseline_source_kind VARCHAR(24);
CREATE INDEX idx_report_rolling_source ON enterprise_report(org_id, period_code, submission_version, voided);

ALTER TABLE report_snapshot ADD COLUMN baseline_source_period_code VARCHAR(32);
ALTER TABLE report_snapshot ADD COLUMN baseline_source_report_id BIGINT;
ALTER TABLE report_snapshot ADD COLUMN baseline_source_kind VARCHAR(24);

ALTER TABLE province_notice ADD COLUMN publisher_level VARCHAR(10) NOT NULL DEFAULT 'PROVINCE';
ALTER TABLE province_notice ADD COLUMN city_org_id BIGINT;
ALTER TABLE province_notice ADD CONSTRAINT fk_notice_city FOREIGN KEY (city_org_id) REFERENCES org_unit(id);
CREATE INDEX idx_notice_city_visible ON province_notice(city_org_id, publisher_user_id, deleted, published_at, id);
