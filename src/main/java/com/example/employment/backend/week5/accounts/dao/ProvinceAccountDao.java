package com.example.employment.backend.week5.accounts.dao;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ProvinceAccountDao {
    private final NamedParameterJdbcTemplate jdbc;
    public ProvinceAccountDao(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<Map<String, Object>> rows(long provinceId, Map<String, String> filters,
            Map<String, List<String>> regionSets, List<String> periodCodes) {
        StringBuilder sql = new StringBuilder("""
                SELECT u.id AS user_id, u.username, u.enabled, u.created_at,
                    o.org_type AS user_type, o.region_code, rg.name AS region_name,
                    COALESCE(f.enterprise_name, o.name) AS unit_name,
                    COALESCE(f.status, 'NO_FILING') AS filing_status,
                    f.nature_code, nature.name AS nature_name,
                    f.industry_code, industry.name AS industry_name,
                    c.region_code AS city_code, cr.name AS city_name,
                    county.code AS county_code, county.name AS county_name,
                    p.region_code AS province_code, pr.name AS province_name,
                    selected.status AS report_status
                FROM user_account u
                JOIN org_unit o ON o.id=u.org_id
                LEFT JOIN org_unit c ON (o.org_type='CITY' AND c.id=o.id)
                    OR (o.org_type='ENTERPRISE' AND c.id=o.parent_id AND c.org_type='CITY')
                JOIN org_unit p ON (o.org_type='PROVINCE' AND p.id=o.id)
                    OR (o.org_type IN ('CITY','ENTERPRISE') AND p.id=c.parent_id)
                JOIN region rg ON rg.code=o.region_code
                JOIN region pr ON pr.code=p.region_code
                LEFT JOIN region cr ON cr.code=c.region_code
                LEFT JOIN region county ON county.code=o.region_code AND county.level=3
                LEFT JOIN enterprise_filing f ON f.org_id=o.id
                LEFT JOIN filing_option nature ON nature.code=f.nature_code
                LEFT JOIN filing_option industry ON industry.code=f.industry_code
                LEFT JOIN enterprise_report selected ON selected.org_id=o.id
                    AND selected.period_code=:selectedPeriod
                WHERE p.id=:provinceId
                """);
        MapSqlParameterSource args = new MapSqlParameterSource()
                .addValue("provinceId", provinceId).addValue("selectedPeriod", filters.get("periodCode"));
        for (String key : List.of("unitName", "username")) if (filters.containsKey(key)) {
            sql.append(" AND LOCATE(LOWER(:").append(key).append("), LOWER(")
                    .append("unitName".equals(key) ? "COALESCE(f.enterprise_name,o.name)" : "u.username")
                    .append("))>0");
            args.addValue(key, filters.get(key));
        }
        if (filters.containsKey("userType")) {
            sql.append(" AND o.org_type=:userType"); args.addValue("userType", filters.get("userType"));
        }
        if (filters.containsKey("dataStatus")) {
            sql.append(" AND o.org_type='ENTERPRISE' AND COALESCE(f.status,'NO_FILING')=:dataStatus");
            args.addValue("dataStatus", filters.get("dataStatus"));
        }
        for (String key : List.of("natureCode", "industryCode")) if (filters.containsKey(key)) {
            sql.append(" AND f.").append("natureCode".equals(key) ? "nature_code" : "industry_code")
                    .append("=:").append(key); args.addValue(key, filters.get(key));
        }
        for (var entry : regionSets.entrySet()) {
            sql.append(" AND o.region_code IN (:").append(entry.getKey()).append(")");
            args.addValue(entry.getKey(), entry.getValue());
        }
        if (filters.containsKey("createdFrom")) {
            sql.append(" AND u.created_at>=:createdFrom"); args.addValue("createdFrom", filters.get("createdFrom"));
        }
        if (filters.containsKey("createdTo")) {
            sql.append(" AND u.created_at<:createdTo"); args.addValue("createdTo", filters.get("createdTo"));
        }
        if (periodCodes != null) {
            sql.append(" AND o.org_type='ENTERPRISE' AND EXISTS (SELECT 1 FROM enterprise_report r")
                    .append(" WHERE r.org_id=o.id AND r.period_code IN (:periodCodes)");
            args.addValue("periodCodes", periodCodes);
            if (filters.containsKey("reportStatus")) {
                sql.append(" AND r.status=:reportStatus");
                args.addValue("reportStatus", filters.get("reportStatus"));
            }
            sql.append(")");
        }
        sql.append(" ORDER BY u.created_at DESC, u.id DESC");
        return jdbc.queryForList(sql.toString(), args);
    }
}
