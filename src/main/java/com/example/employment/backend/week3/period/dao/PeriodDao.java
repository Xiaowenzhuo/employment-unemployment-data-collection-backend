package com.example.employment.backend.week3.period.dao;

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
public class PeriodDao {
    private final JdbcTemplate jdbc;

    public PeriodDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<SurveyPeriod> visible(long provinceId) {
        return jdbc.query("""
                SELECT * FROM survey_period WHERE province_org_id=? OR province_org_id IS NULL
                ORDER BY start_at DESC, code
                """, this::period, provinceId);
    }

    public Optional<SurveyPeriod> byCode(String code, boolean lock) {
        return jdbc.query("SELECT * FROM survey_period WHERE code=?" + (lock ? " FOR UPDATE" : ""),
                this::period, code).stream().findFirst();
    }

    public void insert(SurveyPeriod period) {
        jdbc.update("""
                INSERT INTO survey_period (code, label, start_at, end_exclusive_at, demo,
                province_org_id, open_at, close_at, current_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)
                """, period.code(), period.label(), utc(period.startAt()), utc(period.endExclusiveAt()),
                period.demo(), period.provinceOrgId(), utc(period.openAt()), utc(period.closeAt()));
    }

    public int update(SurveyPeriod period, int expectedVersion) {
        return jdbc.update("""
                UPDATE survey_period SET label=?, start_at=?, end_exclusive_at=?, open_at=?,
                close_at=?, current_version=current_version+1
                WHERE code=? AND current_version=?
                """, period.label(), utc(period.startAt()), utc(period.endExclusiveAt()),
                utc(period.openAt()), utc(period.closeAt()), period.code(), expectedVersion);
    }

    public boolean hasReports(String code) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM enterprise_report WHERE period_code=?", Integer.class, code);
        return count != null && count > 0;
    }

    private SurveyPeriod period(ResultSet rs, int row) throws SQLException {
        return new SurveyPeriod(rs.getString("code"), rs.getString("label"),
                instant(rs, "start_at"), instant(rs, "end_exclusive_at"),
                instant(rs, "open_at"), instant(rs, "close_at"), rs.getBoolean("demo"),
                (Long) rs.getObject("province_org_id"), rs.getInt("current_version"));
    }

    private static Instant instant(ResultSet rs, String field) throws SQLException {
        LocalDateTime value = rs.getObject(field, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static LocalDateTime utc(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
