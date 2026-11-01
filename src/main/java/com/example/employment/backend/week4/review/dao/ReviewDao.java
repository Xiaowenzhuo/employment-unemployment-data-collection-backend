package com.example.employment.backend.week4.review.dao;

import com.example.employment.backend.week4.review.model.ReviewViews.LatestReturn;
import com.example.employment.backend.week4.review.model.ReviewViews.ReviewEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ReviewDao {
    private static final String BASE = """
            FROM enterprise_report r
            JOIN survey_period p ON p.code=r.period_code
            JOIN org_unit e ON e.id=r.org_id
            JOIN org_unit c ON c.id=e.parent_id AND c.org_type='CITY'
            JOIN region rg ON rg.code=e.region_code
            LEFT JOIN report_snapshot s ON s.report_id=r.id
                AND s.submission_version=r.submission_version
            LEFT JOIN filing_version fv ON fv.filing_id=s.filing_id
                AND fv.version=s.filing_version
            LEFT JOIN enterprise_filing f ON f.org_id=e.id
            WHERE r.submission_version>0
            AND ((:level='CITY' AND c.id=:orgId) OR
                 (:level='PROVINCE' AND c.parent_id=:orgId AND EXISTS (
                    SELECT 1 FROM report_review_event ce
                    WHERE ce.report_id=r.id AND ce.level='CITY' AND ce.action='APPROVE')))
            """;
    private static final String SELECT = """
            SELECT r.id, r.org_id, r.period_code, p.label AS period_label,
                COALESCE(fv.enterprise_name, f.enterprise_name, e.name) AS enterprise_name,
                COALESCE(fv.organization_code, f.organization_code) AS organization_code,
                c.id AS city_org_id, c.name AS city_name, e.region_code,
                rg.name AS region_name, r.status, r.current_version, r.submission_version,
                s.baseline_employment_count, s.period_employment_count,
                r.updated_at, r.submitted_at
            """;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public ReviewDao(JdbcTemplate jdbc, NamedParameterJdbcTemplate named) {
        this.jdbc = jdbc;
        this.named = named;
    }

    public List<ReviewRow> list(String level, long orgId, String periodCode, String status,
            int size, int offset) {
        return named.query(SELECT + BASE + " AND r.voided=false" + filters(periodCode, status) +
                " ORDER BY r.updated_at DESC, r.id DESC LIMIT :size OFFSET :offset",
                params(level, orgId, periodCode, status).addValue("size", size).addValue("offset", offset),
                this::row);
    }

    public long count(String level, long orgId, String periodCode, String status) {
        Long value = named.queryForObject("SELECT COUNT(*) " + BASE + " AND r.voided=false" + filters(periodCode, status),
                params(level, orgId, periodCode, status), Long.class);
        return value == null ? 0 : value;
    }

    public Optional<ReviewRow> scoped(long reportId, String level, long orgId) {
        return named.query(SELECT + BASE + " AND r.id=:reportId",
                params(level, orgId, null, null).addValue("reportId", reportId), this::row)
                .stream().findFirst();
    }

    public Optional<LatestReturn> latestReturn(long reportId) {
        return jdbc.query("""
                SELECT level, comment, reviewed_at, reviewer_user_id, reviewer_display_name
                FROM report_review_event WHERE report_id=? AND action='RETURN'
                ORDER BY id DESC LIMIT 1
                """, (rs, index) -> new LatestReturn(rs.getString("level"), rs.getString("comment"),
                instant(rs, "reviewed_at"), Long.toString(rs.getLong("reviewer_user_id")),
                rs.getString("reviewer_display_name")), reportId).stream().findFirst();
    }

    public List<ReviewEvent> events(long reportId) {
        return jdbc.query("""
                SELECT * FROM report_review_event WHERE report_id=?
                ORDER BY reviewed_at ASC, id ASC
                """, (rs, index) -> event(rs), reportId);
    }

    public long nextId() {
        Long id = jdbc.queryForObject("""
                SELECT next_id FROM id_allocator
                WHERE entity_name='report_review_event' FOR UPDATE
                """, Long.class);
        if (id == null) throw new IllegalStateException("Missing review event ID allocator");
        jdbc.update("UPDATE id_allocator SET next_id=? WHERE entity_name='report_review_event'", id + 1);
        return id;
    }

    public int changeStatus(long reportId, String fromStatus, String toStatus,
            int expectedVersion, int submissionVersion, LocalDateTime now) {
        return jdbc.update("""
                UPDATE enterprise_report SET status=?, current_version=current_version+1, updated_at=?
                WHERE id=? AND status=? AND current_version=? AND submission_version=?
                """, toStatus, now, reportId, fromStatus, expectedVersion, submissionVersion);
    }

    public void insertEvent(long id, long reportId, int submissionVersion, String level,
            String action, String fromStatus, String toStatus, long userId, long orgId,
            String displayName, LocalDateTime now, String comment) {
        jdbc.update("""
                INSERT INTO report_review_event (id, report_id, submission_version, level,
                    action, from_status, to_status, reviewer_user_id, reviewer_org_id,
                    reviewer_display_name, reviewed_at, comment)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, reportId, submissionVersion, level, action, fromStatus, toStatus,
                userId, orgId, displayName, now, comment);
    }

    private ReviewRow row(ResultSet rs, int index) throws SQLException {
        return new ReviewRow(rs.getLong("id"), rs.getString("period_code"),
                rs.getString("period_label"), rs.getLong("org_id"), rs.getString("enterprise_name"),
                rs.getString("organization_code"), rs.getLong("city_org_id"),
                rs.getString("city_name"), rs.getString("region_code"),
                rs.getString("region_name"), rs.getString("status"),
                rs.getInt("current_version"), rs.getInt("submission_version"),
                (Integer) rs.getObject("baseline_employment_count"),
                (Integer) rs.getObject("period_employment_count"), instant(rs, "updated_at"),
                instant(rs, "submitted_at"));
    }

    private static ReviewEvent event(ResultSet rs) throws SQLException {
        return new ReviewEvent(Long.toString(rs.getLong("id")), rs.getString("level"),
                rs.getString("action"), rs.getString("from_status"), rs.getString("to_status"),
                rs.getInt("submission_version"), Long.toString(rs.getLong("reviewer_user_id")),
                Long.toString(rs.getLong("reviewer_org_id")), rs.getString("reviewer_display_name"),
                instant(rs, "reviewed_at"), rs.getString("comment"));
    }

    private static Instant instant(ResultSet rs, String field) throws SQLException {
        LocalDateTime value = rs.getObject(field, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static String filters(String periodCode, String status) {
        return (periodCode == null ? "" : " AND r.period_code=:periodCode") +
                (status == null ? "" : " AND r.status=:status");
    }

    private static MapSqlParameterSource params(String level, long orgId,
            String periodCode, String status) {
        return new MapSqlParameterSource().addValue("level", level).addValue("orgId", orgId)
                .addValue("periodCode", periodCode).addValue("status", status);
    }

    public record ReviewRow(long reportId, String periodCode, String periodLabel, long orgId,
            String enterpriseName, String organizationCode, long cityOrgId, String cityName,
            String regionCode, String regionName, String status, int currentVersion,
            int submissionVersion, Integer baselineEmploymentCount, Integer periodEmploymentCount,
            Instant lastActivityAt, Instant submittedAt) { }
}
