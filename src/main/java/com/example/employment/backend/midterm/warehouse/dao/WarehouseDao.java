package com.example.employment.backend.midterm.warehouse.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WarehouseDao {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    public WarehouseDao(JdbcTemplate jdbc,NamedParameterJdbcTemplate named) {
        this.jdbc=jdbc;this.named=named;
    }
    public int replaceSnapshot(LocalDateTime now) {
        jdbc.update("DELETE FROM dw_fact_employment_month");
        jdbc.update("DELETE FROM dw_dim_period");
        jdbc.update("DELETE FROM dw_dim_region");
        jdbc.update("DELETE FROM dw_dim_enterprise");
        jdbc.update("DELETE FROM dw_dim_nature");
        jdbc.update("DELETE FROM dw_dim_industry");
        jdbc.update("""
                INSERT INTO dw_dim_period
                SELECT code,province_org_id,label,start_at,end_exclusive_at
                FROM survey_period WHERE province_org_id IS NOT NULL
                """);
        jdbc.update("""
                INSERT INTO dw_dim_region
                SELECT code,parent_code,name,level FROM region
                """);
        jdbc.update("""
                INSERT INTO dw_dim_enterprise
                SELECT e.id,c.parent_id,c.id,e.region_code,e.name
                FROM org_unit e JOIN org_unit c ON c.id=e.parent_id AND c.org_type='CITY'
                WHERE e.org_type='ENTERPRISE'
                """);
        jdbc.update("""
                INSERT INTO dw_dim_nature SELECT code,parent_code,name
                FROM filing_option WHERE category='NATURE'
                """);
        jdbc.update("""
                INSERT INTO dw_dim_industry SELECT code,parent_code,name
                FROM filing_option WHERE category='INDUSTRY'
                """);
        return jdbc.update("""
                INSERT INTO dw_fact_employment_month (
                    enterprise_org_id,period_code,province_org_id,city_org_id,report_id,
                    submission_version,current_version,snapshot_filing_id,current_filing_id,
                    organization_code,enterprise_name,region_code,nature_parent_code,nature_code,
                    industry_parent_code,industry_code,baseline_source_period_code,
                    baseline_employment_count,period_employment_count,net_change,reduction_count,
                    primary_reason_code,primary_reason_detail,correction_count,valid,synced_at)
                SELECT r.org_id,r.period_code,p.province_org_id,c.id,r.id,
                    r.submission_version,r.current_version,s.filing_id,f.id,
                    COALESCE(fv.organization_code,f.organization_code),
                    COALESCE(fv.enterprise_name,f.enterprise_name,e.name),e.region_code,
                    COALESCE(fv.nature_parent_code,f.nature_parent_code),
                    COALESCE(fv.nature_code,f.nature_code),
                    COALESCE(fv.industry_parent_code,f.industry_parent_code),
                    COALESCE(fv.industry_code,f.industry_code),r.baseline_source_period_code,
                    r.baseline_employment_count,r.period_employment_count,
                    r.period_employment_count-r.baseline_employment_count,
                    GREATEST(r.baseline_employment_count-r.period_employment_count,0),
                    r.primary_reason_code,r.primary_reason_detail,
                    (SELECT COUNT(DISTINCT ce.operation_id) FROM report_correction_event ce
                     WHERE ce.report_id=r.id),true,?
                FROM enterprise_report r JOIN survey_period p ON p.code=r.period_code
                JOIN org_unit e ON e.id=r.org_id AND e.org_type='ENTERPRISE'
                JOIN org_unit c ON c.id=e.parent_id AND c.org_type='CITY'
                LEFT JOIN report_snapshot s ON s.report_id=r.id
                    AND s.submission_version=r.submission_version
                LEFT JOIN filing_version fv ON fv.filing_id=s.filing_id AND fv.version=s.filing_version
                LEFT JOIN enterprise_filing f ON f.org_id=e.id
                WHERE p.province_org_id=c.parent_id AND r.status='APPROVED_PROVINCE'
                    AND r.voided=false AND r.submission_version>0
                """,now);
    }
    public void run(LocalDateTime started,LocalDateTime finished,String result,int count,String error) {
        jdbc.update("INSERT INTO dw_sync_run (started_at,finished_at,result,row_count,error_summary) " +
                "VALUES (?,?,?,?,?)",started,finished,result,count,error);
    }
    public List<Map<String,Object>> rows(long province,String period,List<String> regions) {
        String sql="""
                SELECT f.enterprise_org_id AS org_id,f.period_code,f.report_id,
                    f.submission_version,f.current_version,f.snapshot_filing_id,
                    f.current_filing_id,f.organization_code,f.enterprise_name,
                    f.region_code,rg.name AS region_name,f.city_org_id,
                    c.name AS city_name,c.region_code AS city_region_code,
                    f.nature_parent_code,f.nature_code,n.name AS nature_name,
                    f.industry_parent_code,f.industry_code,i.name AS industry_name,
                    f.baseline_source_period_code,f.baseline_employment_count,
                    f.period_employment_count,f.primary_reason_code,f.primary_reason_detail,
                    f.correction_count
                FROM dw_fact_employment_month f
                JOIN org_unit c ON c.id=f.city_org_id
                JOIN dw_dim_region rg ON rg.region_code=f.region_code
                LEFT JOIN dw_dim_nature n ON n.code=f.nature_code
                LEFT JOIN dw_dim_industry i ON i.code=f.industry_code
                WHERE f.province_org_id=:province AND f.period_code=:period AND f.valid=true
                """;
        MapSqlParameterSource params=new MapSqlParameterSource()
                .addValue("province",province).addValue("period",period);
        if (regions!=null) { sql+=" AND f.region_code IN (:regions)";params.addValue("regions",regions); }
        return named.queryForList(sql+" ORDER BY f.city_org_id,f.enterprise_org_id,f.report_id",params);
    }
}
