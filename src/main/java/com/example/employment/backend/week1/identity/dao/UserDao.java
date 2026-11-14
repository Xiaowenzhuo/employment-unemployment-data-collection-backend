package com.example.employment.backend.week1.identity.dao;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserDao {
    private final JdbcTemplate jdbc;

    public UserDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserRow> findByUsername(String username) {
        return jdbc.query("""
                SELECT u.id, u.org_id, u.username, u.password_hash, u.display_name,
                       u.enabled, o.name AS org_name, o.enabled AS org_enabled,
                       u.auth_version, u.deleted_at
                FROM user_account u JOIN org_unit o ON o.id = u.org_id
                WHERE u.username = ? AND u.deleted_at IS NULL
                """, (rs, rowNum) -> new UserRow(rs.getLong("id"), rs.getLong("org_id"),
                rs.getString("username"), rs.getString("password_hash"),
                rs.getString("display_name"), rs.getBoolean("enabled"),
                rs.getString("org_name"), rs.getBoolean("org_enabled"),
                rs.getLong("auth_version"), rs.getObject("deleted_at") != null), username)
                .stream().findFirst();
    }

    public Optional<UserRow> findById(long id) {
        return jdbc.query("""
                SELECT u.id, u.org_id, u.username, u.password_hash, u.display_name,
                       u.enabled, o.name AS org_name, o.enabled AS org_enabled,
                       u.auth_version, u.deleted_at
                FROM user_account u JOIN org_unit o ON o.id = u.org_id
                WHERE u.id = ?
                """, (rs, rowNum) -> new UserRow(rs.getLong("id"), rs.getLong("org_id"),
                rs.getString("username"), rs.getString("password_hash"),
                rs.getString("display_name"), rs.getBoolean("enabled"),
                rs.getString("org_name"), rs.getBoolean("org_enabled"),
                rs.getLong("auth_version"), rs.getObject("deleted_at") != null), id)
                .stream().findFirst();
    }

    public List<String> roleCodes(long userId) {
        return jdbc.query("""
                SELECT r.code FROM user_role ur JOIN `role` r ON r.id = ur.role_id
                WHERE ur.user_id = ? AND r.role_kind = 'IDENTITY' ORDER BY r.code
                """, (rs, rowNum) -> rs.getString("code"), userId);
    }

    public List<com.example.employment.backend.week1.identity.model.UserAccount.FunctionalRole>
            functionalRoles(long userId) {
        return jdbc.query("""
                SELECT r.id,r.code,r.name,r.level_code FROM user_role ur
                JOIN `role` r ON r.id=ur.role_id
                WHERE ur.user_id=? AND r.role_kind='FUNCTION' AND r.deleted_at IS NULL
                ORDER BY r.code,r.id
                """, (rs, row) ->
                new com.example.employment.backend.week1.identity.model.UserAccount.FunctionalRole(
                        Long.toString(rs.getLong("id")),rs.getString("code"),
                        rs.getString("name"),rs.getString("level_code")),userId);
    }

    public List<String> permissions(long userId) {
        return jdbc.query("""
                SELECT DISTINCT rp.permission_code FROM user_role ur
                JOIN `role` r ON r.id=ur.role_id
                JOIN role_permission rp ON rp.role_id=r.id
                WHERE ur.user_id=? AND r.role_kind='FUNCTION' AND r.deleted_at IS NULL
                ORDER BY rp.permission_code
                """,(rs,row)->rs.getString(1),userId);
    }

    public record UserRow(long id, long orgId, String username, String passwordHash,
            String displayName, boolean enabled, String orgName, boolean orgEnabled,
            long authVersion, boolean deleted) {
    }
}
