package com.example.employment.backend.cube.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CubeDao {
    private final JdbcTemplate jdbc;
    public CubeDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<Map<String,Object>> regions(String provinceCode) {
        return jdbc.queryForList("""
                SELECT code,parent_code,name,level FROM region
                WHERE code=? OR parent_code=? OR parent_code IN
                    (SELECT code FROM region WHERE parent_code=?)
                ORDER BY level,code
                """,provinceCode,provinceCode,provinceCode);
    }
    public List<Map<String,Object>> options(String category) {
        return jdbc.queryForList("SELECT code,parent_code,name FROM filing_option "
                + "WHERE category=? ORDER BY parent_code,code",category);
    }
    public List<LocalDateTime> periods(long provinceId) {
        return jdbc.query("SELECT start_at FROM survey_period WHERE province_org_id=? "
                + "ORDER BY start_at,code",(rs,i)->rs.getObject(1,LocalDateTime.class),provinceId);
    }
    public List<Fact> facts(long provinceId) {
        return jdbc.query("""
                SELECT f.enterprise_org_id,f.period_code,f.region_code,
                       c.region_code AS city_code,f.nature_code,f.industry_code,
                       f.industry_parent_code,f.baseline_employment_count,
                       f.period_employment_count,p.start_at
                FROM dw_fact_employment_month f
                JOIN survey_period p ON p.code=f.period_code
                JOIN org_unit c ON c.id=f.city_org_id AND c.org_type='CITY'
                WHERE f.province_org_id=? AND f.valid=true AND p.province_org_id=?
                ORDER BY p.start_at,f.enterprise_org_id
                """,(rs,i)->new Fact(rs.getLong("enterprise_org_id"),rs.getString("period_code"),
                rs.getString("region_code"),rs.getString("city_code"),rs.getString("nature_code"),
                rs.getString("industry_code"),rs.getString("industry_parent_code"),
                rs.getLong("baseline_employment_count"),rs.getLong("period_employment_count"),
                rs.getObject("start_at",LocalDateTime.class)),provinceId,provinceId);
    }
    public record Fact(long enterpriseId,String periodCode,String regionCode,String cityCode,
            String natureCode,String industryCode,String industryParentCode,long baseline,
            long current,LocalDateTime startAt) { }
}
