package com.example.employment.backend.national.dao;

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
public class NationalDao {
    private final JdbcTemplate jdbc;
    public NationalDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void lockReports(String period) {
        jdbc.query("SELECT id FROM enterprise_report WHERE period_code=? ORDER BY id FOR UPDATE",
                (rs, i) -> rs.getLong(1), period);
    }
    public boolean containsReport(long id) {
        return !jdbc.query("SELECT report_id FROM national_batch_item WHERE report_id=? FOR UPDATE",
                (rs, i) -> rs.getLong(1), id).isEmpty() ||
                !jdbc.query("SELECT source_report_id FROM national_proxy_item WHERE source_report_id=? AND batch_id IS NOT NULL FOR UPDATE",
                (rs, i) -> rs.getLong(1), id).isEmpty();
    }
    public List<SourceRow> source(long province, String period) {
        return jdbc.query("""
                SELECT e.id AS org_id,e.region_code,e.name AS org_name,
                    c.region_code AS city_code,c.name AS city_name,
                    f.status AS filing_status,f.organization_code AS current_org_code,
                    f.enterprise_name AS current_enterprise_name,
                    f.nature_code AS current_nature_code,f.industry_code AS current_industry_code,
                    fv.organization_code AS version_org_code,
                    fv.enterprise_name AS version_enterprise_name,
                    fv.nature_code AS version_nature_code,fv.industry_code AS version_industry_code,
                    r.id AS report_id,r.status AS report_status,r.voided,
                    r.submission_version,r.current_version,r.baseline_source_period_code,
                    r.baseline_employment_count,r.period_employment_count,
                    r.reduction_type_code,r.primary_reason_code,r.primary_reason_detail,r.other_reason
                FROM org_unit e
                JOIN org_unit c ON c.id=e.parent_id AND c.org_type='CITY'
                LEFT JOIN enterprise_filing f ON f.org_id=e.id
                LEFT JOIN enterprise_report r ON r.org_id=e.id AND r.period_code=?
                LEFT JOIN report_snapshot s ON s.report_id=r.id
                    AND s.submission_version=r.submission_version
                LEFT JOIN filing_version fv ON fv.filing_id=s.filing_id AND fv.version=s.filing_version
                WHERE e.org_type='ENTERPRISE' AND c.parent_id=?
                ORDER BY c.region_code,e.region_code,e.id
                """, this::source, period, province);
    }
    public Optional<BatchRow> byPeriod(long province, String period) {
        return jdbc.query("SELECT * FROM national_batch WHERE province_org_id=? AND period_code=?",
                this::batch, province, period).stream().findFirst();
    }
    public Optional<BatchRow> byId(long province, String id) {
        return jdbc.query("SELECT * FROM national_batch WHERE province_org_id=? AND batch_id=?",
                this::batch, province, id).stream().findFirst();
    }
    public Optional<BatchRow> byId(String id) {
        return jdbc.query("SELECT * FROM national_batch WHERE batch_id=?", this::batch, id)
                .stream().findFirst();
    }
    public List<BatchRow> list(long province, String period, String status, int page, int size) {
        return jdbc.query("""
                SELECT * FROM national_batch WHERE province_org_id=?
                    AND (? IS NULL OR period_code=?) AND (? IS NULL OR status=?)
                ORDER BY created_at DESC,batch_id DESC LIMIT ? OFFSET ?
                """, this::batch, province, period, period, status, status,
                size, (long) page * size);
    }
    public long count(long province, String period, String status) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM national_batch WHERE province_org_id=?
                    AND (? IS NULL OR period_code=?) AND (? IS NULL OR status=?)
                """, Long.class, province, period, period, status, status);
        return count == null ? 0 : count;
    }
    public void insert(BatchRow b) {
        jdbc.update("""
                INSERT INTO national_batch (batch_id,province_org_id,period_code,mode,status,
                    payload_json,payload_sha256,enterprise_count,baseline_total,period_total,
                    net_change,created_at)
                VALUES (?,?,?,'MOCK','QUEUED',?,?,?,?,?,?,?)
                """, b.batchId(), b.provinceOrgId(), b.periodCode(), b.payloadJson(),
                b.payloadSha256(), b.enterpriseCount(), b.baselineTotal(), b.periodTotal(),
                b.netChange(), utc(b.createdAt()));
    }
    public void insertItem(String batch, long report, int submission, int version,
            int baseline, int current) {
        jdbc.update("""
                INSERT INTO national_batch_item (batch_id,report_id,submission_version,
                    current_version,baseline_employment_count,period_employment_count)
                VALUES (?,?,?,?,?,?)
                """, batch, report, submission, version, baseline, current);
    }
    public int queueRetry(String batch) {
        return jdbc.update("UPDATE national_batch SET status='QUEUED',last_error=NULL " +
                "WHERE batch_id=? AND status='SEND_FAILED'", batch);
    }
    public boolean start(String batch, int number, Instant now) {
        if (jdbc.update("UPDATE national_batch SET status='SENDING',last_sent_at=? " +
                "WHERE batch_id=? AND status='QUEUED'", utc(now), batch) != 1) return false;
        jdbc.update("INSERT INTO national_send_attempt " +
                "(batch_id,attempt_no,started_at,result) VALUES (?,?,?,'SENDING')",
                batch, number, utc(now));
        return true;
    }
    public int attemptCount(String batch) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM national_send_attempt WHERE batch_id=?",
                Integer.class, batch);
        return count == null ? 0 : count;
    }
    public void accepted(String batch, int number, String receipt, Instant received, Instant now) {
        jdbc.update("UPDATE national_batch SET status='MOCK_ACCEPTED',receipt_id=?," +
                "receipt_at=?,last_error=NULL WHERE batch_id=? AND status='SENDING'",
                receipt, utc(received), batch);
        jdbc.update("UPDATE national_send_attempt SET finished_at=?,result='ACCEPTED'," +
                "mock_receipt_id=? WHERE batch_id=? AND attempt_no=?",
                utc(now), receipt, batch, number);
    }
    public void failed(String batch, int number, String error, Instant now) {
        jdbc.update("UPDATE national_batch SET status='SEND_FAILED',last_error=? " +
                "WHERE batch_id=? AND status='SENDING'", error, batch);
        jdbc.update("UPDATE national_send_attempt SET finished_at=?,result='FAILED'," +
                "error_summary=? WHERE batch_id=? AND attempt_no=?", utc(now), error, batch, number);
    }
    public void recover(Instant now) {
        jdbc.update("UPDATE national_send_attempt SET finished_at=?,result='FAILED'," +
                "error_summary='服务重启，原发送未完成' WHERE result='SENDING' AND batch_id IN (SELECT batch_id FROM national_batch WHERE schema_version='demo-1')", utc(now));
        jdbc.update("UPDATE national_batch SET status='SEND_FAILED',last_error='服务重启，原发送未完成' " +
                "WHERE status='SENDING' AND schema_version='demo-1'");
    }
    public List<String> queued() {
        return jdbc.query("SELECT batch_id FROM national_batch WHERE status='QUEUED' AND schema_version='demo-1'",
                (rs, i) -> rs.getString(1));
    }
    public List<AttemptRow> attempts(String batch) {
        return jdbc.query("SELECT * FROM national_send_attempt WHERE batch_id=? ORDER BY attempt_no",
                (rs, i) -> new AttemptRow(rs.getInt("attempt_no"), instant(rs, "started_at"),
                        instant(rs, "finished_at"), rs.getString("result"),
                        rs.getString("error_summary"), rs.getString("mock_receipt_id")), batch);
    }
    private SourceRow source(ResultSet rs, int i) throws SQLException {
        return new SourceRow(rs.getLong("org_id"), rs.getString("region_code"),
                rs.getString("org_name"), rs.getString("city_code"), rs.getString("city_name"),
                rs.getString("filing_status"), rs.getString("current_org_code"),
                rs.getString("current_enterprise_name"), rs.getString("current_nature_code"),
                rs.getString("current_industry_code"), rs.getString("version_org_code"),
                rs.getString("version_enterprise_name"), rs.getString("version_nature_code"),
                rs.getString("version_industry_code"), (Long) rs.getObject("report_id"),
                rs.getString("report_status"), rs.getBoolean("voided"),
                (Integer) rs.getObject("submission_version"),
                (Integer) rs.getObject("current_version"), rs.getString("baseline_source_period_code"),
                (Integer) rs.getObject("baseline_employment_count"),
                (Integer) rs.getObject("period_employment_count"),
                rs.getString("reduction_type_code"), rs.getString("primary_reason_code"),
                rs.getString("primary_reason_detail"), rs.getString("other_reason"));
    }
    private BatchRow batch(ResultSet rs, int i) throws SQLException {
        return new BatchRow(rs.getString("batch_id"), rs.getLong("province_org_id"),
                rs.getString("period_code"), rs.getString("status"), rs.getString("payload_json"),
                rs.getString("payload_sha256"), rs.getInt("enterprise_count"),
                rs.getLong("baseline_total"), rs.getLong("period_total"),
                rs.getLong("net_change"), instant(rs, "created_at"),
                instant(rs, "last_sent_at"), rs.getString("last_error"),
                rs.getString("receipt_id"), instant(rs, "receipt_at"));
    }
    private static Instant instant(ResultSet rs, String name) throws SQLException {
        LocalDateTime value = rs.getObject(name, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
    private static LocalDateTime utc(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
    public record SourceRow(long orgId, String regionCode, String orgName,
            String cityCode, String cityName, String filingStatus,
            String currentOrgCode, String currentEnterpriseName, String currentNatureCode,
            String currentIndustryCode, String versionOrgCode, String versionEnterpriseName,
            String versionNatureCode, String versionIndustryCode, Long reportId,
            String reportStatus, boolean voided, Integer submissionVersion,
            Integer currentVersion, String baselineSourcePeriodCode,
            Integer baselineCount, Integer periodCount, String reductionTypeCode,
            String primaryReasonCode, String primaryReasonDetail, String otherReason) { }
    public record BatchRow(String batchId, long provinceOrgId, String periodCode, String status,
            String payloadJson, String payloadSha256, int enterpriseCount, long baselineTotal,
            long periodTotal, long netChange, Instant createdAt, Instant lastSentAt,
            String lastError, String receiptId, Instant receiptAt) { }
    public record AttemptRow(int attemptNo, Instant startedAt, Instant finishedAt,
            String result, String errorSummary, String mockReceiptId) { }
}
