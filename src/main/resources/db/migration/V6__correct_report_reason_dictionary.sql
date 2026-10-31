-- Correct the V4 demo dictionary against the teacher's original reason hierarchy.
-- Keep the existing codes so historical reports and snapshots remain readable.
UPDATE report_option
SET parent_type_code='TYPE_OTHER', parent_code='REASON_OTHER'
WHERE code IN ('REASON_VOLUNTARY_LEAVE', 'REASON_JOB_TRANSFER', 'REASON_LABOR_TRANSFER');

INSERT INTO report_option (code, category, name, parent_type_code) VALUES
('REASON_INTERNATIONAL_CHANGE', 'REASON', '国际因素变化', 'TYPE_INTERNATIONAL');

INSERT INTO report_option (code, category, name, parent_type_code, parent_code) VALUES
('REASON_RETIREMENT', 'REASON', '退休', 'TYPE_NATURAL_ATTRITION', 'REASON_NATURAL_ATTRITION'),
('REASON_RETIREMENT_FROM_POST', 'REASON', '退职', 'TYPE_NATURAL_ATTRITION', 'REASON_NATURAL_ATTRITION'),
('REASON_DEATH', 'REASON', '死亡', 'TYPE_NATURAL_ATTRITION', 'REASON_NATURAL_ATTRITION');
