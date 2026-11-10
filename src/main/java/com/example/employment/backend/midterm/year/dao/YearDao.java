package com.example.employment.backend.midterm.year.dao;

import com.example.employment.backend.week3.period.model.SurveyPeriod;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class YearDao {
    private final JdbcTemplate jdbc;
    public YearDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void lockProvince(long provinceId) {
        jdbc.queryForObject("SELECT id FROM org_unit WHERE id=? FOR UPDATE", Long.class, provinceId);
    }
    public List<SurveyPeriod> provincePeriods(long provinceId) {
        return jdbc.query("SELECT * FROM survey_period WHERE province_org_id=? ORDER BY start_at,code",
                this::period, provinceId);
    }
    public Optional<SurveyPeriod> byCode(String code) {
        return jdbc.query("SELECT * FROM survey_period WHERE code=?", this::period, code)
                .stream().findFirst();
    }
    public void insert(SurveyPeriod p) {
        jdbc.update("""
                INSERT INTO survey_period (code,label,start_at,end_exclusive_at,demo,
                    province_org_id,open_at,close_at,current_version)
                VALUES (?,?,?,?,false,?,?,?,1)
                """, p.code(), p.label(), utc(p.startAt()), utc(p.endExclusiveAt()),
                p.provinceOrgId(), utc(p.openAt()), utc(p.closeAt()));
    }
    private SurveyPeriod period(ResultSet rs, int i) throws SQLException {
        return new SurveyPeriod(rs.getString("code"), rs.getString("label"),
                instant(rs,"start_at"),instant(rs,"end_exclusive_at"),instant(rs,"open_at"),
                instant(rs,"close_at"),rs.getBoolean("demo"),
                (Long)rs.getObject("province_org_id"),rs.getInt("current_version"));
    }
    private static Instant instant(ResultSet rs,String column) throws SQLException {
        LocalDateTime value=rs.getObject(column,LocalDateTime.class);
        return value==null?null:value.toInstant(ZoneOffset.UTC);
    }
    private static LocalDateTime utc(Instant value) {
        return LocalDateTime.ofInstant(value,ZoneOffset.UTC);
    }
}
