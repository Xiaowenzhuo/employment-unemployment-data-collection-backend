ALTER TABLE survey_period ADD COLUMN province_org_id BIGINT;
ALTER TABLE survey_period ADD COLUMN open_at DATETIME(3);
ALTER TABLE survey_period ADD COLUMN close_at DATETIME(3);
ALTER TABLE survey_period ADD COLUMN current_version INT NOT NULL DEFAULT 1;
ALTER TABLE survey_period ADD CONSTRAINT fk_period_province
    FOREIGN KEY (province_org_id) REFERENCES org_unit(id);
CREATE INDEX idx_period_province ON survey_period(province_org_id, start_at);

INSERT INTO id_allocator VALUES ('enterprise_report', 1);

CREATE TABLE report_option (
    code VARCHAR(64) PRIMARY KEY,
    category VARCHAR(10) NOT NULL,
    name VARCHAR(120) NOT NULL,
    parent_type_code VARCHAR(64),
    parent_code VARCHAR(64),
    CONSTRAINT fk_report_option_type FOREIGN KEY (parent_type_code) REFERENCES report_option(code),
    CONSTRAINT fk_report_option_parent FOREIGN KEY (parent_code) REFERENCES report_option(code)
);
CREATE INDEX idx_report_option_category ON report_option(category, parent_type_code, parent_code);

INSERT INTO report_option (code, category, name) VALUES
('TYPE_CLOSURE', 'TYPE', '关闭破产'),
('TYPE_SUSPENSION', 'TYPE', '停业整顿'),
('TYPE_ECONOMIC_LAYOFF', 'TYPE', '经济性裁员'),
('TYPE_BUSINESS_TRANSFER', 'TYPE', '业务转移'),
('TYPE_NATURAL_ATTRITION', 'TYPE', '自然减员'),
('TYPE_CONTRACT_END', 'TYPE', '正常解除或终止劳动合同'),
('TYPE_INTERNATIONAL', 'TYPE', '国际因素变化影响'),
('TYPE_DISASTER', 'TYPE', '自然灾害'),
('TYPE_MAJOR_EVENT', 'TYPE', '重大事件影响'),
('TYPE_OTHER', 'TYPE', '其他');

INSERT INTO report_option (code, category, name, parent_type_code) VALUES
('REASON_INDUSTRIAL_RESTRUCTURING', 'REASON', '产业结构调整', 'TYPE_ECONOMIC_LAYOFF'),
('REASON_TECH_REFORM', 'REASON', '重大技术改革', 'TYPE_ECONOMIC_LAYOFF'),
('REASON_ENERGY_SAVING', 'REASON', '节能减排、淘汰落后产能', 'TYPE_ECONOMIC_LAYOFF'),
('REASON_INSUFFICIENT_ORDERS', 'REASON', '订单不足', 'TYPE_ECONOMIC_LAYOFF'),
('REASON_RAW_MATERIAL_COST', 'REASON', '原材料涨价', 'TYPE_ECONOMIC_LAYOFF'),
('REASON_LABOR_COST', 'REASON', '工资、社保等用工成本上升', 'TYPE_ECONOMIC_LAYOFF'),
('REASON_FUNDING', 'REASON', '经营资金困难', 'TYPE_CLOSURE'),
('REASON_TAX_POLICY', 'REASON', '税收政策变化（包括税负增加或出口退税减少等）', 'TYPE_ECONOMIC_LAYOFF'),
('REASON_SEASONAL', 'REASON', '季节性用工', 'TYPE_CONTRACT_END'),
('REASON_NATURAL_ATTRITION', 'REASON', '自然减员', 'TYPE_NATURAL_ATTRITION'),
('REASON_OTHER_TRANSFER', 'REASON', '其他', 'TYPE_BUSINESS_TRANSFER'),
('REASON_OTHER_SUSPENSION', 'REASON', '其他', 'TYPE_SUSPENSION'),
('REASON_OTHER_INTERNATIONAL', 'REASON', '其他', 'TYPE_INTERNATIONAL'),
('REASON_OTHER_DISASTER', 'REASON', '其他', 'TYPE_DISASTER'),
('REASON_OTHER_MAJOR_EVENT', 'REASON', '其他', 'TYPE_MAJOR_EVENT'),
('REASON_OTHER', 'REASON', '其他', 'TYPE_OTHER');

INSERT INTO report_option (code, category, name, parent_type_code, parent_code) VALUES
('REASON_VOLUNTARY_LEAVE', 'REASON', '自行离职', 'TYPE_NATURAL_ATTRITION', 'REASON_NATURAL_ATTRITION'),
('REASON_JOB_TRANSFER', 'REASON', '工作调动、企业内部调剂', 'TYPE_NATURAL_ATTRITION', 'REASON_NATURAL_ATTRITION'),
('REASON_LABOR_TRANSFER', 'REASON', '劳动关系转移、劳务派遣', 'TYPE_NATURAL_ATTRITION', 'REASON_NATURAL_ATTRITION');

CREATE TABLE enterprise_report (
    id BIGINT PRIMARY KEY,
    org_id BIGINT NOT NULL,
    period_code VARCHAR(32) NOT NULL,
    status VARCHAR(20) NOT NULL,
    current_version INT NOT NULL,
    submission_version INT NOT NULL,
    baseline_employment_count INT,
    period_employment_count INT,
    other_reason VARCHAR(500),
    reduction_type_code VARCHAR(64),
    primary_reason_code VARCHAR(64),
    primary_reason_detail VARCHAR(500),
    secondary_reason_code VARCHAR(64),
    secondary_reason_detail VARCHAR(500),
    tertiary_reason_code VARCHAR(64),
    tertiary_reason_detail VARCHAR(500),
    updated_at DATETIME(3) NOT NULL,
    submitted_at DATETIME(3),
    UNIQUE (org_id, period_code),
    CONSTRAINT fk_report_org FOREIGN KEY (org_id) REFERENCES org_unit(id),
    CONSTRAINT fk_report_period FOREIGN KEY (period_code) REFERENCES survey_period(code)
);
CREATE INDEX idx_report_org_status ON enterprise_report(org_id, status, updated_at);

CREATE TABLE enterprise_baseline (
    org_id BIGINT PRIMARY KEY,
    employment_count INT NOT NULL,
    source_report_id BIGINT NOT NULL,
    CONSTRAINT fk_baseline_org FOREIGN KEY (org_id) REFERENCES org_unit(id),
    CONSTRAINT fk_baseline_report FOREIGN KEY (source_report_id) REFERENCES enterprise_report(id)
);

CREATE TABLE report_snapshot (
    report_id BIGINT NOT NULL,
    submission_version INT NOT NULL,
    baseline_employment_count INT NOT NULL,
    period_employment_count INT NOT NULL,
    other_reason VARCHAR(500) NOT NULL,
    reduction_type_code VARCHAR(64),
    primary_reason_code VARCHAR(64),
    primary_reason_detail VARCHAR(500),
    secondary_reason_code VARCHAR(64),
    secondary_reason_detail VARCHAR(500),
    tertiary_reason_code VARCHAR(64),
    tertiary_reason_detail VARCHAR(500),
    submitted_at DATETIME(3) NOT NULL,
    submitted_by BIGINT NOT NULL,
    result VARCHAR(20) NOT NULL,
    PRIMARY KEY (report_id, submission_version),
    CONSTRAINT fk_snapshot_report FOREIGN KEY (report_id) REFERENCES enterprise_report(id),
    CONSTRAINT fk_snapshot_user FOREIGN KEY (submitted_by) REFERENCES user_account(id)
);
