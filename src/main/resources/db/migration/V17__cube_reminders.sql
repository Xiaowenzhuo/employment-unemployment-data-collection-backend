ALTER TABLE province_notice ADD COLUMN targeted BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE notice_recipient (
    notice_id BIGINT NOT NULL,
    org_id BIGINT NOT NULL,
    PRIMARY KEY (notice_id,org_id),
    CONSTRAINT fk_notice_recipient_notice FOREIGN KEY (notice_id) REFERENCES province_notice(id),
    CONSTRAINT fk_notice_recipient_org FOREIGN KEY (org_id) REFERENCES org_unit(id)
);
CREATE TABLE notice_reminder_preview (
    preview_id VARCHAR(36) PRIMARY KEY,
    actor_user_id BIGINT NOT NULL,
    province_org_id BIGINT NOT NULL,
    period_code VARCHAR(32) NOT NULL,
    audience VARCHAR(16) NOT NULL,
    recipient_hash CHAR(64) NOT NULL,
    city_count INT NOT NULL,
    enterprise_count INT NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    notice_id BIGINT,
    content_hash CHAR(64),
    CONSTRAINT fk_reminder_actor FOREIGN KEY (actor_user_id) REFERENCES user_account(id),
    CONSTRAINT fk_reminder_province FOREIGN KEY (province_org_id) REFERENCES org_unit(id),
    CONSTRAINT fk_reminder_period FOREIGN KEY (period_code) REFERENCES survey_period(code),
    CONSTRAINT fk_reminder_notice FOREIGN KEY (notice_id) REFERENCES province_notice(id)
);
CREATE INDEX idx_reminder_expiry ON notice_reminder_preview(expires_at);
INSERT INTO permission_route (http_method,path_pattern,identity_code,permission_code) VALUES
('GET','/province/analysis/cube/metadata','PROVINCE','PROVINCE.ANALYSIS.READ'),
('POST','/province/analysis/cube/query','PROVINCE','PROVINCE.ANALYSIS.READ'),
('POST','/province/analysis/cube/cell-detail','PROVINCE','PROVINCE.ANALYSIS.READ'),
('POST','/province/notices/report-reminders/preview','PROVINCE','PROVINCE.NOTICE.CREATE'),
('POST','/province/notices/report-reminders','PROVINCE','PROVINCE.NOTICE.CREATE');
