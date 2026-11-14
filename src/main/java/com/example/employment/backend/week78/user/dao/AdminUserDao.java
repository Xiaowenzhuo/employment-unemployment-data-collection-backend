package com.example.employment.backend.week78.user.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AdminUserDao {
    private final JdbcTemplate jdbc;

    public AdminUserDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<OrgRow> organization(long id, long provinceId) {
        return jdbc.query("""
                SELECT o.id,o.name,o.org_type,o.region_code,r.name AS region_name,
                       o.parent_id,o.enabled
                FROM org_unit o JOIN region r ON r.code=o.region_code
                LEFT JOIN org_unit parent ON parent.id=o.parent_id
                WHERE o.id=? AND (o.id=? OR o.parent_id=? OR parent.parent_id=?)
                """, (rs, row) -> org(rs), id, provinceId, provinceId, provinceId)
                .stream().findFirst();
    }

    public List<OrgRow> organizations(long provinceId, String type, String keyword,
            int page, int size) {
        List<Object> args = new ArrayList<>();
        String where = organizationFilter(provinceId, type, keyword, args);
        args.add(size);
        args.add((long) page * size);
        return jdbc.query("""
                SELECT o.id,o.name,o.org_type,o.region_code,r.name AS region_name,
                       o.parent_id,o.enabled
                FROM org_unit o JOIN region r ON r.code=o.region_code
                LEFT JOIN org_unit parent ON parent.id=o.parent_id
                """ + where + """
                ORDER BY CASE o.org_type WHEN 'PROVINCE' THEN 0 WHEN 'CITY' THEN 1 ELSE 2 END,
                         o.region_code,o.name,o.id LIMIT ? OFFSET ?
                """, (rs, row) -> org(rs), args.toArray());
    }

    public long organizationCount(long provinceId, String type, String keyword) {
        List<Object> args = new ArrayList<>();
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM org_unit o
                LEFT JOIN org_unit parent ON parent.id=o.parent_id
                """ + organizationFilter(provinceId, type, keyword, args),
                Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    private String organizationFilter(long provinceId, String type, String keyword,
            List<Object> args) {
        StringBuilder where = new StringBuilder("""
                WHERE o.enabled=true AND (o.id=? OR o.parent_id=? OR parent.parent_id=?)
                """);
        args.add(provinceId);
        args.add(provinceId);
        args.add(provinceId);
        if (type != null) {
            where.append(" AND o.org_type=?");
            args.add(type);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND o.name LIKE ?");
            args.add("%" + keyword.trim() + "%");
        }
        return " " + where;
    }

    public Optional<UserRow> user(long id, long provinceId, boolean lock) {
        String sql = """
                SELECT u.id,u.username,u.display_name,u.enabled,u.version,u.created_at,
                       o.id AS org_id,o.name,o.org_type,o.region_code,
                       r.name AS region_name,o.parent_id,o.enabled AS org_enabled
                FROM user_account u JOIN org_unit o ON o.id=u.org_id
                JOIN region r ON r.code=o.region_code
                LEFT JOIN org_unit parent ON parent.id=o.parent_id
                WHERE u.id=? AND u.deleted_at IS NULL
                AND (o.id=? OR o.parent_id=? OR parent.parent_id=?)
                """ + (lock ? " FOR UPDATE" : "");
        return jdbc.query(sql, (rs, row) -> user(rs), id, provinceId, provinceId, provinceId)
                .stream().findFirst();
    }

    public List<UserRow> users(long provinceId, Long orgId, String roleCode, Boolean enabled,
            String keyword, Long functionalRoleId, int page, int size) {
        List<Object> args = new ArrayList<>();
        String where = userFilter(provinceId, orgId, roleCode, enabled, keyword,
                functionalRoleId, args);
        args.add(size);
        args.add((long) page * size);
        return jdbc.query("""
                SELECT u.id,u.username,u.display_name,u.enabled,u.version,u.created_at,
                       o.id AS org_id,o.name,o.org_type,o.region_code,
                       r.name AS region_name,o.parent_id,o.enabled AS org_enabled
                FROM user_account u JOIN org_unit o ON o.id=u.org_id
                JOIN region r ON r.code=o.region_code
                LEFT JOIN org_unit parent ON parent.id=o.parent_id
                """ + where + " ORDER BY u.created_at DESC,u.id DESC LIMIT ? OFFSET ?",
                (rs, row) -> user(rs), args.toArray());
    }

    public long userCount(long provinceId, Long orgId, String roleCode, Boolean enabled,
            String keyword, Long functionalRoleId) {
        List<Object> args = new ArrayList<>();
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_account u JOIN org_unit o ON o.id=u.org_id
                LEFT JOIN org_unit parent ON parent.id=o.parent_id
                """ + userFilter(provinceId, orgId, roleCode, enabled, keyword,
                functionalRoleId, args), Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    private String userFilter(long provinceId, Long orgId, String roleCode, Boolean enabled,
            String keyword, Long functionalRoleId, List<Object> args) {
        StringBuilder where = new StringBuilder("""
                WHERE u.deleted_at IS NULL
                AND (o.id=? OR o.parent_id=? OR parent.parent_id=?)
                """);
        args.add(provinceId);
        args.add(provinceId);
        args.add(provinceId);
        if (orgId != null) {
            where.append(" AND o.id=?");
            args.add(orgId);
        }
        if (roleCode != null) {
            where.append(" AND o.org_type=?");
            args.add(roleCode);
        }
        if (enabled != null) {
            where.append(" AND u.enabled=?");
            args.add(enabled);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (u.username LIKE ? OR u.display_name LIKE ?)");
            args.add("%" + keyword.trim() + "%");
            args.add("%" + keyword.trim() + "%");
        }
        if (functionalRoleId != null) {
            where.append("""
                    AND EXISTS (SELECT 1 FROM user_role ur WHERE ur.user_id=u.id AND ur.role_id=?)
                    """);
            args.add(functionalRoleId);
        }
        return " " + where;
    }

    private OrgRow org(ResultSet rs) throws SQLException {
        return new OrgRow(rs.getLong("id"),rs.getString("name"),rs.getString("org_type"),
                rs.getString("region_code"),rs.getString("region_name"),
                (Long)rs.getObject("parent_id"),rs.getBoolean("enabled"));
    }

    private UserRow user(ResultSet rs) throws SQLException {
        return new UserRow(rs.getLong("id"),rs.getString("username"),
                rs.getString("display_name"),rs.getBoolean("enabled"),rs.getLong("version"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                new OrgRow(rs.getLong("org_id"),rs.getString("name"),
                        rs.getString("org_type"),rs.getString("region_code"),
                        rs.getString("region_name"),(Long)rs.getObject("parent_id"),
                        rs.getBoolean("org_enabled")));
    }

    public boolean usernameExists(String name) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_account WHERE LOWER(username)=LOWER(?)",
                Long.class, name);
        return count != null && count > 0;
    }

    public long nextId() {
        Long id = jdbc.queryForObject("""
                SELECT next_id FROM id_allocator WHERE entity_name='user_account' FOR UPDATE
                """, Long.class);
        if (id == null) throw new IllegalStateException("Missing user allocator");
        jdbc.update("UPDATE id_allocator SET next_id=? WHERE entity_name='user_account'", id + 1);
        return id;
    }

    public void create(long id, long orgId, String username, String name, String hash,
            String identity, List<Long> roleIds, LocalDateTime now) {
        jdbc.update("""
                INSERT INTO user_account (id,org_id,username,password_hash,display_name,
                enabled,created_at) VALUES (?,?,?,?,?,true,?)
                """, id, orgId, username, hash, name, now);
        jdbc.update("""
                INSERT INTO user_role (user_id,role_id)
                SELECT ?,id FROM `role` WHERE code=?
                """, id, identity);
        for (long roleId : roleIds) {
            jdbc.update("INSERT INTO user_role (user_id,role_id) VALUES (?,?)", id, roleId);
        }
    }

    public void update(long id, String name, long version, List<Long> roleIds) {
        jdbc.update("""
                UPDATE user_account SET display_name=?,version=version+1
                WHERE id=? AND version=?
                """, name, id, version);
        jdbc.update("""
                DELETE FROM user_role WHERE user_id=?
                AND role_id IN (SELECT id FROM `role` WHERE role_kind='FUNCTION')
                """, id);
        for (long roleId : roleIds) {
            jdbc.update("INSERT INTO user_role (user_id,role_id) VALUES (?,?)", id, roleId);
        }
    }

    public void status(long id, boolean enabled, long version) {
        jdbc.update("UPDATE user_account SET enabled=?,version=version+1 WHERE id=? AND version=?",
                enabled, id, version);
    }

    public void reset(long id, String hash, long version) {
        jdbc.update("""
                UPDATE user_account SET password_hash=?,auth_version=auth_version+1,
                version=version+1 WHERE id=? AND version=?
                """, hash, id, version);
    }

    public void delete(long id, long version, LocalDateTime now) {
        jdbc.update("""
                UPDATE user_account SET enabled=false,deleted_at=?,version=version+1
                WHERE id=? AND version=?
                """, now, id, version);
    }

    public List<String> historyTypes(long id) {
        List<String> result = new ArrayList<>();
        if (exists("SELECT COUNT(*) FROM report_snapshot WHERE submitted_by=?", id))
            result.add("REPORT_SUBMISSION");
        if (exists("SELECT COUNT(*) FROM review_record WHERE reviewer_id=?", id))
            result.add("FILING_REVIEW");
        if (exists("SELECT COUNT(*) FROM report_review_event WHERE reviewer_user_id=?", id))
            result.add("REPORT_REVIEW");
        return result;
    }

    private boolean exists(String sql, long id) {
        Long count = jdbc.queryForObject(sql, Long.class, id);
        return count != null && count > 0;
    }

    public void audit(long actorId, String action, long targetId, LocalDateTime now) {
        jdbc.update("""
                INSERT INTO admin_audit_event (actor_user_id,action,target_user_id,event_at)
                VALUES (?,?,?,?)
                """, actorId, action, targetId, now);
    }

    public record OrgRow(long id, String name, String type, String regionCode,
            String regionName, Long parentId, boolean enabled) {
    }
    public record UserRow(long id, String username, String displayName, boolean enabled,
            long version, LocalDateTime createdAt, OrgRow org) {
    }
}
