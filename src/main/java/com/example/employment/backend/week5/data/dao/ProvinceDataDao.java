package com.example.employment.backend.week5.data.dao;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ProvinceDataDao {
    private final NamedParameterJdbcTemplate jdbc;
    public ProvinceDataDao(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<Map<String, Object>> rows(long provinceId, String periodCode, List<String> regions) {
        String sql = """
                SELECT r.id AS report_id, r.org_id, r.period_code, r.current_version,
                    r.submission_version, r.baseline_employment_count, r.period_employment_count,
                    r.other_reason, r.reduction_type_code, r.primary_reason_code,
                    r.primary_reason_detail, r.secondary_reason_code, r.secondary_reason_detail,
                    r.tertiary_reason_code, r.tertiary_reason_detail,
                    s.baseline_employment_count AS original_baseline,
                    s.period_employment_count AS original_period,
                    s.other_reason AS original_other, s.reduction_type_code AS original_type,
                    s.primary_reason_code AS original_primary, s.primary_reason_detail AS original_primary_detail,
                    s.secondary_reason_code AS original_secondary, s.secondary_reason_detail AS original_secondary_detail,
                    s.tertiary_reason_code AS original_tertiary, s.tertiary_reason_detail AS original_tertiary_detail,
                    s.filing_id AS snapshot_filing_id, s.filing_version AS snapshot_filing_version,
                    e.region_code, rg.name AS region_name, c.id AS city_org_id,
                    c.name AS city_name, c.region_code AS city_region_code,
                    COALESCE(fv.enterprise_name, f.enterprise_name, e.name) AS enterprise_name,
                    COALESCE(fv.organization_code, f.organization_code) AS organization_code,
                    COALESCE(fv.nature_code, f.nature_code) AS nature_code,
                    COALESCE(fv.nature_parent_code, f.nature_parent_code) AS nature_parent_code,
                    COALESCE(fv.industry_code, f.industry_code) AS industry_code,
                    COALESCE(fv.industry_parent_code, f.industry_parent_code) AS industry_parent_code,
                    COALESCE(fv.main_business, f.main_business) AS main_business,
                    COALESCE(fv.contact_name, f.contact_name) AS contact_name,
                    COALESCE(fv.address_code, f.address_code) AS address_code,
                    COALESCE(fv.postal_code, f.postal_code) AS postal_code,
                    COALESCE(fv.contact_phone, f.contact_phone) AS contact_phone,
                    COALESCE(fv.fax, f.fax) AS fax, COALESCE(fv.email, f.email) AS email,
                    f.id AS current_filing_id,
                    nature.name AS nature_name, industry.name AS industry_name,
                    address.name AS address_name,
                    (SELECT COUNT(DISTINCT ce.operation_id) FROM report_correction_event ce
                     WHERE ce.report_id=r.id) AS correction_count,
                    (SELECT MAX(ce.occurred_at) FROM report_correction_event ce
                     WHERE ce.report_id=r.id) AS last_corrected_at
                FROM enterprise_report r
                JOIN survey_period p ON p.code=r.period_code
                JOIN org_unit e ON e.id=r.org_id AND e.org_type='ENTERPRISE'
                JOIN org_unit c ON c.id=e.parent_id AND c.org_type='CITY'
                JOIN region rg ON rg.code=e.region_code
                JOIN report_snapshot s ON s.report_id=r.id
                    AND s.submission_version=r.submission_version
                LEFT JOIN filing_version fv ON fv.filing_id=s.filing_id
                    AND fv.version=s.filing_version
                LEFT JOIN enterprise_filing f ON f.org_id=e.id
                LEFT JOIN filing_option nature ON nature.code=COALESCE(fv.nature_code,f.nature_code)
                LEFT JOIN filing_option industry ON industry.code=COALESCE(fv.industry_code,f.industry_code)
                LEFT JOIN filing_option address ON address.code=COALESCE(fv.address_code,f.address_code)
                WHERE c.parent_id=:provinceId AND p.province_org_id=:provinceId
                    AND r.period_code=:periodCode AND r.status='APPROVED_PROVINCE'
                    AND r.voided=false
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("provinceId", provinceId).addValue("periodCode", periodCode);
        if (regions != null) {
            sql += " AND e.region_code IN (:regions)";
            params.addValue("regions", regions);
        }
        return jdbc.queryForList(sql + " ORDER BY c.id, e.id, r.id", params);
    }
}
