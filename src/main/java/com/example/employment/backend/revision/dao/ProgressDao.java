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
                       r.submitted_at, r.submission_version,
                       CASE WHEN f.status='APPROVED' AND
                           COALESCE((SELECT MIN(v.reviewed_at) FROM filing_version v
                                     WHERE v.filing_id=f.id AND v.result='APPROVED'),
                                    f.updated_at)<p.end_exclusive_at
                           THEN true ELSE false END AS eligible,
                       px.source_period_code,px.source_report_id,
                       px.source_submission_version,px.source_current_version,
                       px.employment_count AS proxy_employment_count
                FROM org_unit e JOIN org_unit c ON c.id=e.parent_id AND c.org_type='CITY'
                JOIN survey_period p ON p.code=?
                LEFT JOIN enterprise_filing f ON f.org_id=e.id
                LEFT JOIN enterprise_report r ON r.org_id=e.id AND r.period_code=?
                LEFT JOIN national_proxy_item px ON px.enterprise_org_id=e.id AND px.period_code=?
                WHERE e.org_type='ENTERPRISE' AND c.parent_id=?
                ORDER BY c.region_code,e.id
                """,(rs,i)->{
                    LocalDateTime submitted=rs.getObject("submitted_at",LocalDateTime.class);
                    return new Row(rs.getLong("enterprise_id"),rs.getString("enterprise_name"),
                            rs.getString("city_code"),rs.getString("city_name"),
                            rs.getString("filing_status"),
                            rs.getObject("report_id")==null?null:rs.getLong("report_id"),
                            rs.getString("report_status"),rs.getBoolean("voided"),
                            submitted==null?null:submitted.toInstant(ZoneOffset.UTC),
                            rs.getObject("submission_version")==null?0:rs.getInt("submission_version"),
                            rs.getBoolean("eligible"),rs.getString("source_period_code"),
                            (Long)rs.getObject("source_report_id"),
                            (Integer)rs.getObject("source_submission_version"),
                            (Integer)rs.getObject("source_current_version"),
                            (Integer)rs.getObject("proxy_employment_count"));
                },periodCode,periodCode,periodCode,provinceId);
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
            java.time.Instant submittedAt,int submissionVersion,boolean eligible,
            String proxySourcePeriodCode,Long proxySourceReportId,
            Integer proxySubmissionVersion,Integer proxyCurrentVersion,
            Integer proxyEmploymentCount) {
        public String effectiveReportStatus() {
            return voided ? "VOIDED" : reportStatus==null ? "NOT_SUBMITTED" : reportStatus;
        }
        public boolean realSubmitted() { return !voided && submissionVersion>0; }
        public String coverageMode() {
            return !eligible ? "NONE" : realSubmitted() ? "REAL" :
                    proxySourceReportId!=null ? "PROXY" : "NONE";
        }
    }
    public record City(String code,String name) { }
}
