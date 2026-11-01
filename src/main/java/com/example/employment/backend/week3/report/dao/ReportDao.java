package com.example.employment.backend.week3.report.dao;

import com.example.employment.backend.week3.report.model.Report;
import com.example.employment.backend.week3.report.model.ReportForm;
import com.example.employment.backend.week3.report.model.ReportViews.ListItem;
import com.example.employment.backend.week3.report.model.ReportViews.Snapshot;
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
public class ReportDao {
    private static final String FORM_COLUMNS = """
            baseline_employment_count, period_employment_count, other_reason, reduction_type_code,
            primary_reason_code, primary_reason_detail, secondary_reason_code, secondary_reason_detail,
            tertiary_reason_code, tertiary_reason_detail
            """;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public ReportDao(JdbcTemplate jdbc, NamedParameterJdbcTemplate named) {
        this.jdbc = jdbc;
        this.named = named;
    }

    public Optional<Report> byOrgPeriod(long orgId, String periodCode, boolean lock) {
        return jdbc.query("SELECT * FROM enterprise_report WHERE org_id=? AND period_code=?" +
                (lock ? " FOR UPDATE" : ""), this::report, orgId, periodCode).stream().findFirst();
    }

    public Optional<Report> byId(long id, boolean lock) {
        return jdbc.query("SELECT * FROM enterprise_report WHERE id=?" +
                (lock ? " FOR UPDATE" : ""), this::report, id).stream().findFirst();
    }

    public long nextId() {
        Long next = jdbc.queryForObject("""
                SELECT next_id FROM id_allocator WHERE entity_name='enterprise_report' FOR UPDATE
                """, Long.class);
        if (next == null) throw new IllegalStateException("Missing report ID allocator");
        jdbc.update("UPDATE id_allocator SET next_id=? WHERE entity_name='enterprise_report'", next + 1);
        return next;
    }

    public void lockOrg(long orgId) {
        jdbc.queryForObject("SELECT id FROM org_unit WHERE id=? FOR UPDATE", Long.class, orgId);
    }

    public void insert(long id, long orgId, String periodCode, ReportForm form, LocalDateTime now) {
        jdbc.update("INSERT INTO enterprise_report (id, org_id, period_code, status, current_version, " +
                "submission_version, " + FORM_COLUMNS + ", updated_at) VALUES (?, ?, ?, 'DRAFT', 1, 0, " +
                "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, orgId, periodCode,
                form.baselineEmploymentCount(), form.periodEmploymentCount(), form.otherReason(),
                form.reductionTypeCode(), form.primaryReasonCode(), form.primaryReasonDetail(),
                form.secondaryReasonCode(), form.secondaryReasonDetail(),
                form.tertiaryReasonCode(), form.tertiaryReasonDetail(), now);
    }

    public int updateDraft(long id, int expectedVersion, ReportForm form, LocalDateTime now) {
        return jdbc.update("""
                UPDATE enterprise_report SET baseline_employment_count=?, period_employment_count=?,
                other_reason=?, reduction_type_code=?, primary_reason_code=?, primary_reason_detail=?,
                secondary_reason_code=?, secondary_reason_detail=?, tertiary_reason_code=?,
                tertiary_reason_detail=?, current_version=current_version+1, updated_at=?
                WHERE id=? AND status IN ('DRAFT','RETURNED_CITY','RETURNED_PROVINCE') AND current_version=?
                """, form.baselineEmploymentCount(), form.periodEmploymentCount(), form.otherReason(),
                form.reductionTypeCode(), form.primaryReasonCode(), form.primaryReasonDetail(),
                form.secondaryReasonCode(), form.secondaryReasonDetail(), form.tertiaryReasonCode(),
                form.tertiaryReasonDetail(), now, id, expectedVersion);
    }

    public int submit(long id, int expectedVersion, LocalDateTime now) {
        return jdbc.update("""
                UPDATE enterprise_report SET status='SUBMITTED_CITY',
                current_version=current_version+1, submission_version=submission_version+1,
                updated_at=?, submitted_at=?
                WHERE id=? AND status IN ('DRAFT','RETURNED_CITY','RETURNED_PROVINCE') AND current_version=?
                """, now, now, id, expectedVersion);
    }

    public Optional<Integer> baseline(long orgId) {
        return jdbc.query("SELECT employment_count FROM enterprise_baseline WHERE org_id=?",
                (rs, row) -> rs.getInt(1), orgId).stream().findFirst();
    }

    public void insertBaseline(long orgId, int count, long reportId) {
        jdbc.update("INSERT INTO enterprise_baseline VALUES (?, ?, ?)", orgId, count, reportId);
    }

    public void snapshot(Report report, int version, long actorId, LocalDateTime now,
            long filingId, int filingVersion) {
        ReportForm form = report.form();
        jdbc.update("INSERT INTO report_snapshot (report_id, submission_version, " + FORM_COLUMNS +
                ", submitted_at, submitted_by, result, filing_id, filing_version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'SUBMITTED_CITY', ?, ?)",
                report.id(), version, form.baselineEmploymentCount(), form.periodEmploymentCount(),
                form.otherReason(), form.reductionTypeCode(), form.primaryReasonCode(),
                form.primaryReasonDetail(), form.secondaryReasonCode(), form.secondaryReasonDetail(),
                form.tertiaryReasonCode(), form.tertiaryReasonDetail(), now, actorId,
                filingId, filingVersion);
    }

    public List<Snapshot> snapshots(long reportId) {
        return jdbc.query("""
                SELECT s.*, u.username FROM report_snapshot s
                JOIN user_account u ON u.id=s.submitted_by
                WHERE s.report_id=? ORDER BY s.submission_version DESC
                """, (rs, row) -> new Snapshot(rs.getInt("submission_version"), form(rs),
                instant(rs, "submitted_at"), rs.getString("username"),
                rs.getString("result"), rs.getObject("filing_id") == null ? null :
                        Long.toString(rs.getLong("filing_id")),
                (Integer) rs.getObject("filing_version")), reportId);
    }

    public List<ListItem> list(long orgId, String periodCode, String status, int size, int offset) {
        return named.query("""
                SELECT r.*, p.label AS period_label FROM enterprise_report r
                JOIN survey_period p ON p.code=r.period_code
                WHERE r.org_id=:orgId
                """ + filters(periodCode, status) + " ORDER BY r.updated_at DESC, r.id DESC LIMIT :size OFFSET :offset",
                params(orgId, periodCode, status).addValue("size", size).addValue("offset", offset),
                (rs, row) -> new ListItem(Long.toString(rs.getLong("id")),
                        rs.getString("period_code"), rs.getString("period_label"),
                        rs.getString("status"), rs.getInt("current_version"),
                        rs.getInt("submission_version"), instant(rs, "submitted_at"), null,
                        rs.getBoolean("voided"), instant(rs, "voided_at")));
    }

    public long count(long orgId, String periodCode, String status) {
        Long count = named.queryForObject("SELECT COUNT(*) FROM enterprise_report r WHERE r.org_id=:orgId" +
                filters(periodCode, status), params(orgId, periodCode, status), Long.class);
        return count == null ? 0 : count;
    }

    public List<OptionRow> options() {
        return jdbc.query("SELECT code, category, name, parent_type_code, parent_code " +
                "FROM report_option ORDER BY code", (rs, row) -> new OptionRow(
                rs.getString("code"), rs.getString("category"), rs.getString("name"),
                rs.getString("parent_type_code"), rs.getString("parent_code")));
    }

    private static String filters(String periodCode, String status) {
        return (periodCode == null ? "" : " AND r.period_code=:periodCode") +
                (status == null ? "" : " AND r.status=:status");
    }

    private static MapSqlParameterSource params(long orgId, String periodCode, String status) {
        return new MapSqlParameterSource().addValue("orgId", orgId).addValue("periodCode", periodCode)
                .addValue("status", status);
    }

    private Report report(ResultSet rs, int row) throws SQLException {
        return new Report(rs.getLong("id"), rs.getLong("org_id"), rs.getString("period_code"),
                rs.getString("status"), rs.getInt("current_version"),
                rs.getInt("submission_version"), form(rs), instant(rs, "updated_at"),
                instant(rs, "submitted_at"));
    }

    private static ReportForm form(ResultSet rs) throws SQLException {
        return new ReportForm((Integer) rs.getObject("baseline_employment_count"),
                (Integer) rs.getObject("period_employment_count"), rs.getString("other_reason"),
                rs.getString("reduction_type_code"), rs.getString("primary_reason_code"),
                rs.getString("primary_reason_detail"), rs.getString("secondary_reason_code"),
                rs.getString("secondary_reason_detail"), rs.getString("tertiary_reason_code"),
                rs.getString("tertiary_reason_detail"));
    }

    private static Instant instant(ResultSet rs, String field) throws SQLException {
        LocalDateTime value = rs.getObject(field, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    public record OptionRow(String code, String category, String name,
            String parentTypeCode, String parentCode) { }
}
