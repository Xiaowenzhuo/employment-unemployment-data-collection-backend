package com.example.employment.backend.week5.correction.dao;

import com.example.employment.backend.week3.report.model.ReportForm;
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
public class CorrectionDao {
    private final JdbcTemplate jdbc;
    public CorrectionDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean voided(long reportId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT voided FROM enterprise_report WHERE id=?", Boolean.class, reportId));
    }
    public Instant voidedAt(long reportId) {
        LocalDateTime value = jdbc.queryForObject(
                "SELECT voided_at FROM enterprise_report WHERE id=?", LocalDateTime.class, reportId);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
    public long nextId(String entity) {
        Long id = jdbc.queryForObject("SELECT next_id FROM id_allocator WHERE entity_name=? FOR UPDATE",
                Long.class, entity);
        if (id == null) throw new IllegalStateException("Missing allocator: " + entity);
        jdbc.update("UPDATE id_allocator SET next_id=? WHERE entity_name=?", id + 1, entity);
        return id;
    }
    public int update(long id, int version, int submission, ReportForm form, LocalDateTime now) {
        return jdbc.update("""
                UPDATE enterprise_report SET period_employment_count=?, other_reason=?,
                    reduction_type_code=?, primary_reason_code=?, primary_reason_detail=?,
                    secondary_reason_code=?, secondary_reason_detail=?, tertiary_reason_code=?,
                    tertiary_reason_detail=?, current_version=current_version+1, updated_at=?
                WHERE id=? AND status='APPROVED_PROVINCE' AND voided=false
                    AND current_version=? AND submission_version=?
                """, form.periodEmploymentCount(), form.otherReason(), form.reductionTypeCode(),
                form.primaryReasonCode(), form.primaryReasonDetail(), form.secondaryReasonCode(),
                form.secondaryReasonDetail(), form.tertiaryReasonCode(), form.tertiaryReasonDetail(),
                now, id, version, submission);
    }
    public int voidReport(long id, int version, int submission, LocalDateTime now) {
        return jdbc.update("""
                UPDATE enterprise_report SET voided=true, voided_at=?,
                    current_version=current_version+1, updated_at=?
                WHERE id=? AND status='APPROVED_PROVINCE' AND voided=false
                    AND current_version=? AND submission_version=?
                """, now, now, id, version, submission);
    }
    public void insertCorrection(long id, long operation, long report, int submission,
            String field, Object oldValue, Object newValue, String reason, long actor,
            String actorName, LocalDateTime now) {
        jdbc.update("""
                INSERT INTO report_correction_event (id, operation_id, report_id,
                    submission_version, field_name, old_value, new_value, reason,
                    actor_user_id, actor_display_name, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, operation, report, submission, field,
                oldValue == null ? null : oldValue.toString(),
                newValue == null ? null : newValue.toString(), reason, actor, actorName, now);
    }
    public void insertVoid(long id, long report, int submission, String reason,
            long actor, String actorName, LocalDateTime now) {
        jdbc.update("""
                INSERT INTO report_void_event (id, report_id, submission_version, reason,
                    actor_user_id, actor_display_name, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, report, submission, reason, actor, actorName, now);
    }
    public List<CorrectionEntry> corrections(long reportId) {
        return jdbc.query("SELECT * FROM report_correction_event WHERE report_id=? ORDER BY id",
                (rs, row) -> new CorrectionEntry(Long.toString(rs.getLong("id")),
                Long.toString(rs.getLong("operation_id")), rs.getInt("submission_version"),
                rs.getString("field_name"), value(rs.getString("field_name"), rs.getString("old_value")),
                value(rs.getString("field_name"), rs.getString("new_value")), rs.getString("reason"),
                Long.toString(rs.getLong("actor_user_id")), rs.getString("actor_display_name"),
                instant(rs, "occurred_at")), reportId);
    }
    public Optional<VoidEvent> voidEvent(long reportId) {
        return jdbc.query("SELECT * FROM report_void_event WHERE report_id=?",
                (rs, row) -> new VoidEvent(Long.toString(rs.getLong("id")), rs.getString("reason"),
                Long.toString(rs.getLong("actor_user_id")), rs.getString("actor_display_name"),
                instant(rs, "occurred_at"), rs.getInt("submission_version")), reportId)
                .stream().findFirst();
    }
    private static Object value(String field, String value) {
        return value == null ? null : "periodEmploymentCount".equals(field)
                ? Integer.valueOf(value) : value;
    }
    private static Instant instant(ResultSet rs, String key) throws SQLException {
        return rs.getObject(key, LocalDateTime.class).toInstant(ZoneOffset.UTC);
    }
    public record CorrectionEntry(String eventId, String operationId, int submissionVersion,
            String fieldName, Object oldValue, Object newValue, String reason,
            String actorUserId, String actorDisplayName, Instant occurredAt) { }
    public record VoidEvent(String eventId, String reason, String actorUserId,
            String actorDisplayName, Instant occurredAt, int submissionVersion) { }
}
