package com.example.employment.backend.revision.dao;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ProgressDao {
    private final JdbcTemplate jdbc;
    public ProgressDao(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public List<Row> rows(long provinceId,String periodCode) {
        return jdbc.query("""
                SELECT e.id AS enterprise_id, COALESCE(f.enterprise_name,e.name) AS enterprise_name,
                       c.region_code AS city_code, c.name AS city_name,
                       COALESCE(f.status,'NO_FILING') AS filing_status,
                       r.id AS report_id, r.status AS report_status, r.voided,
                       r.submitted_at
                FROM org_unit e JOIN org_unit c ON c.id=e.parent_id AND c.org_type='CITY'
                LEFT JOIN enterprise_filing f ON f.org_id=e.id
                LEFT JOIN enterprise_report r ON r.org_id=e.id AND r.period_code=?
                WHERE e.org_type='ENTERPRISE' AND c.parent_id=?
                ORDER BY c.region_code,e.id
                """,(rs,i)->{
                    LocalDateTime submitted=rs.getObject("submitted_at",LocalDateTime.class);
                    return new Row(rs.getLong("enterprise_id"),rs.getString("enterprise_name"),
                            rs.getString("city_code"),rs.getString("city_name"),
                            rs.getString("filing_status"),
                            rs.getObject("report_id")==null?null:rs.getLong("report_id"),
                            rs.getString("report_status"),rs.getBoolean("voided"),
                            submitted==null?null:submitted.toInstant(ZoneOffset.UTC));
                },periodCode,provinceId);
    }

    public List<City> cities(long provinceId) {
        return jdbc.query("""
                SELECT region_code, name FROM org_unit
                WHERE parent_id=? AND org_type='CITY' ORDER BY region_code,id
                """,(rs,i)->new City(rs.getString("region_code"),rs.getString("name")),
                provinceId);
    }

    public record Row(long enterpriseId,String enterpriseName,String cityCode,String cityName,
            String filingStatus,Long reportId,String reportStatus,boolean voided,
            java.time.Instant submittedAt) {
        public String effectiveReportStatus() {
            return voided ? "VOIDED" : reportStatus==null ? "NOT_SUBMITTED" : reportStatus;
        }
    }
    public record City(String code,String name) { }
}
