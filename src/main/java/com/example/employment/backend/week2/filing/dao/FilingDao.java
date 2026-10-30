package com.example.employment.backend.week2.filing.dao;

import com.example.employment.backend.week2.filing.model.Filing;
import com.example.employment.backend.week2.filing.model.FilingVersion;
import com.example.employment.backend.week2.filing.model.FilingViews.Period;
import com.example.employment.backend.week2.filing.model.Profile;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class FilingDao {
    private static final String PROFILE_COLUMNS = """
            organization_code, enterprise_name, nature_parent_code, nature_code,
            industry_parent_code, industry_code, main_business, contact_name,
            address_parent_code, address_code, postal_code, contact_phone, fax, email
            """;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public FilingDao(JdbcTemplate jdbc, NamedParameterJdbcTemplate named) {
        this.jdbc = jdbc;
        this.named = named;
    }

    public Optional<Filing> byOrg(long orgId, boolean lock) {
        return jdbc.query("SELECT * FROM enterprise_filing WHERE org_id = ?" +
                        (lock ? " FOR UPDATE" : ""), this::filing, orgId).stream().findFirst();
    }

    public Optional<Filing> byId(long id, boolean lock) {
        return jdbc.query("SELECT * FROM enterprise_filing WHERE id = ?" +
                        (lock ? " FOR UPDATE" : ""), this::filing, id).stream().findFirst();
    }

    public long allocateFilingId() {
        Long next = jdbc.queryForObject("""
                SELECT next_id FROM id_allocator
                WHERE entity_name = 'enterprise_filing' FOR UPDATE
                """, Long.class);
        if (next == null) {
            throw new IllegalStateException("Missing filing ID allocator");
        }
        jdbc.update("UPDATE id_allocator SET next_id = ? WHERE entity_name = 'enterprise_filing'", next + 1);
        return next;
    }

    public void insertDraft(long id, long orgId, Profile p, LocalDateTime now) {
        jdbc.update("""
                INSERT INTO enterprise_filing (id, org_id, status, current_version,
                """ + PROFILE_COLUMNS + ", updated_at) VALUES (?, ?, 'DRAFT', 0, " +
                "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, orgId, p.organizationCode(), p.enterpriseName(), p.natureParentCode(),
                p.natureCode(), p.industryParentCode(), p.industryCode(), p.mainBusiness(),
                p.contactName(), p.addressParentCode(), p.addressCode(), p.postalCode(),
                p.contactPhone(), p.fax(), p.email(), now);
    }

    public void updateDraft(long id, Profile p, LocalDateTime now) {
        jdbc.update("""
                UPDATE enterprise_filing SET organization_code=?, enterprise_name=?,
                nature_parent_code=?, nature_code=?, industry_parent_code=?, industry_code=?,
                main_business=?, contact_name=?, address_parent_code=?, address_code=?,
                postal_code=?, contact_phone=?, fax=?, email=?, updated_at=? WHERE id=?
                """, p.organizationCode(), p.enterpriseName(), p.natureParentCode(),
                p.natureCode(), p.industryParentCode(), p.industryCode(), p.mainBusiness(),
                p.contactName(), p.addressParentCode(), p.addressCode(), p.postalCode(),
                p.contactPhone(), p.fax(), p.email(), now, id);
    }

    public int setPending(long id, String oldStatus, int oldVersion, LocalDateTime now) {
        return jdbc.update("""
                UPDATE enterprise_filing SET status='PENDING', current_version=?, updated_at=?
                WHERE id=? AND status=? AND current_version=?
                """, oldVersion + 1, now, id, oldStatus, oldVersion);
    }

    public void insertVersion(long id, int version, Profile p, long actorId, LocalDateTime now) {
        jdbc.update("""
                INSERT INTO filing_version (filing_id, version, result,
                """ + PROFILE_COLUMNS + ", submitted_at, submitted_by) VALUES (?, ?, 'PENDING', " +
                "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, version, p.organizationCode(), p.enterpriseName(), p.natureParentCode(),
                p.natureCode(), p.industryParentCode(), p.industryCode(), p.mainBusiness(),
                p.contactName(), p.addressParentCode(), p.addressCode(), p.postalCode(),
                p.contactPhone(), p.fax(), p.email(), now, actorId);
    }

    public Optional<FilingVersion> version(long id, int version) {
        return jdbc.query("SELECT * FROM filing_version WHERE filing_id=? AND version=?",
                this::version, id, version).stream().findFirst();
    }

    public List<FilingVersion> history(long id) {
        return jdbc.query("SELECT * FROM filing_version WHERE filing_id=? ORDER BY version DESC",
                this::version, id);
    }

    public int setReviewed(long id, int version, String result, long reviewer,
            String reason, LocalDateTime now) {
        int changed = jdbc.update("""
                UPDATE enterprise_filing SET status=?, updated_at=?
                WHERE id=? AND status='PENDING' AND current_version=?
                """, result, now, id, version);
        if (changed == 1) {
            jdbc.update("""
                    UPDATE filing_version SET result=?, reviewed_at=?, reviewed_by=?, reason=?
                    WHERE filing_id=? AND version=? AND result='PENDING'
                    """, result, now, reviewer, reason, id, version);
            jdbc.update("""
                    INSERT INTO review_record (filing_id, version, action, reviewer_id, reviewed_at, reason)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, id, version, result, reviewer, now, reason);
        }
        return changed;
    }

    public void syncOrgName(long orgId, String name) {
        jdbc.update("UPDATE org_unit SET name=? WHERE id=?", name, orgId);
    }

    public boolean optionPair(String category, String parent, String child) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM filing_option p JOIN filing_option c ON c.parent_code=p.code
                WHERE p.category=? AND c.category=? AND p.code=? AND c.code=?
                """, Integer.class, category, category, parent, child);
        return count != null && count > 0;
    }

    public boolean codeExists(String code, long excludingFilingId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM enterprise_filing
                WHERE organization_code=? AND id<>?
                """, Integer.class, code, excludingFilingId);
        return count != null && count > 0;
    }

    public List<OptionRow> options(String category) {
        return jdbc.query("""
                SELECT code, parent_code, name, demo FROM filing_option
                WHERE category=? ORDER BY code
                """, (rs, row) -> new OptionRow(rs.getString("code"),
                rs.getString("parent_code"), rs.getString("name"), rs.getBoolean("demo")), category);
    }

    public Optional<String> optionName(String code) {
        return jdbc.query("SELECT name FROM filing_option WHERE code=?",
                (rs, row) -> rs.getString("name"), code).stream().findFirst();
    }

    public List<Period> periods() {
        return jdbc.query("SELECT * FROM survey_period ORDER BY start_at DESC",
                (rs, row) -> new Period(rs.getString("code"), rs.getString("label"),
                        instant(rs, "start_at"), instant(rs, "end_exclusive_at"),
                        rs.getBoolean("demo")));
    }

    public Optional<Period> period(String code) {
        return jdbc.query("SELECT * FROM survey_period WHERE code=?",
                (rs, row) -> new Period(rs.getString("code"), rs.getString("label"),
                        instant(rs, "start_at"), instant(rs, "end_exclusive_at"),
                        rs.getBoolean("demo")), code).stream().findFirst();
    }

    public List<ListRow> list(long provinceOrgId, List<String> regions, String status,
            LocalDateTime endExclusive, Integer limit, Integer offset) {
        String sql = """
                SELECT f.id, f.org_id, f.status, f.current_version, o.region_code,
                       v.organization_code, v.enterprise_name, v.nature_parent_code,
                       v.nature_code, v.industry_parent_code, v.industry_code,
                       v.main_business, v.contact_name, v.address_parent_code,
                       v.address_code, v.postal_code, v.contact_phone, v.fax, v.email,
                       v.result, v.submitted_at, v.reviewed_at, v.reason
                FROM enterprise_filing f
                JOIN org_unit o ON o.id=f.org_id
                JOIN org_unit city ON city.id=o.parent_id AND city.org_type='CITY'
                JOIN filing_version v ON v.filing_id=f.id AND v.version=f.current_version
                WHERE city.parent_id=:provinceId AND f.status=:status
                  AND o.region_code IN (:regions)
                """ + (endExclusive == null ? "" : " AND v.reviewed_at < :endExclusive") +
                " ORDER BY v.submitted_at DESC, f.id DESC" +
                (limit == null ? "" : " LIMIT :limit OFFSET :offset");
        MapSqlParameterSource params = filters(provinceOrgId, regions, status, endExclusive);
        if (limit != null) {
            params.addValue("limit", limit).addValue("offset", offset);
        }
        return named.query(sql, params, (rs, row) -> new ListRow(rs.getLong("id"),
                rs.getLong("org_id"), rs.getString("status"), rs.getInt("current_version"),
                rs.getString("region_code"), profile(rs), instant(rs, "submitted_at"),
                instant(rs, "reviewed_at")));
    }

    public long count(long provinceOrgId, List<String> regions, String status,
            LocalDateTime endExclusive) {
        String sql = """
                SELECT COUNT(*) FROM enterprise_filing f
                JOIN org_unit o ON o.id=f.org_id
                JOIN org_unit city ON city.id=o.parent_id AND city.org_type='CITY'
                JOIN filing_version v ON v.filing_id=f.id AND v.version=f.current_version
                WHERE city.parent_id=:provinceId AND f.status=:status
                  AND o.region_code IN (:regions)
                """ + (endExclusive == null ? "" : " AND v.reviewed_at < :endExclusive");
        Long count = named.queryForObject(sql,
                filters(provinceOrgId, regions, status, endExclusive), Long.class);
        return count == null ? 0 : count;
    }

    public void audit(long actorId, long orgId, String action, String detail,
            LocalDateTime now) {
        jdbc.update("""
                INSERT INTO audit_event
                (event_id, actor_user_id, action, target_org_id, occurred_at, detail_code)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), actorId, action, orgId, now, detail);
    }

    private MapSqlParameterSource filters(long provinceOrgId, List<String> regions,
            String status, LocalDateTime endExclusive) {
        return new MapSqlParameterSource().addValue("provinceId", provinceOrgId)
                .addValue("regions", regions).addValue("status", status)
                .addValue("endExclusive", endExclusive);
    }

    private Filing filing(ResultSet rs, int row) throws SQLException {
        return new Filing(rs.getLong("id"), rs.getLong("org_id"), rs.getString("status"),
                rs.getInt("current_version"), profile(rs), instant(rs, "updated_at"));
    }

    private FilingVersion version(ResultSet rs, int row) throws SQLException {
        return new FilingVersion(rs.getLong("filing_id"), rs.getInt("version"),
                rs.getString("result"), profile(rs), instant(rs, "submitted_at"),
                instant(rs, "reviewed_at"), rs.getString("reason"));
    }

    private Profile profile(ResultSet rs) throws SQLException {
        return new Profile(rs.getString("organization_code"), rs.getString("enterprise_name"),
                rs.getString("nature_parent_code"), rs.getString("nature_code"),
                rs.getString("industry_parent_code"), rs.getString("industry_code"),
                rs.getString("main_business"), rs.getString("contact_name"),
                rs.getString("address_parent_code"), rs.getString("address_code"),
                rs.getString("postal_code"), rs.getString("contact_phone"),
                rs.getString("fax"), rs.getString("email"));
    }

    private Instant instant(ResultSet rs, String field) throws SQLException {
        LocalDateTime value = rs.getObject(field, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    public record OptionRow(String code, String parentCode, String name, boolean demo) {
    }

    public record ListRow(long id, long orgId, String status, int currentVersion,
            String regionCode, Profile profile, Instant submittedAt, Instant reviewedAt) {
    }
}
