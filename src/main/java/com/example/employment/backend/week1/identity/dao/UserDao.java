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
                       u.enabled, o.name AS org_name, o.enabled AS org_enabled
                FROM user_account u JOIN org_unit o ON o.id = u.org_id
                WHERE u.username = ?
                """, (rs, rowNum) -> new UserRow(rs.getLong("id"), rs.getLong("org_id"),
                rs.getString("username"), rs.getString("password_hash"),
                rs.getString("display_name"), rs.getBoolean("enabled"),
                rs.getString("org_name"), rs.getBoolean("org_enabled")), username)
                .stream().findFirst();
    }

    public Optional<UserRow> findById(long id) {
        return jdbc.query("""
                SELECT u.id, u.org_id, u.username, u.password_hash, u.display_name,
                       u.enabled, o.name AS org_name, o.enabled AS org_enabled
                FROM user_account u JOIN org_unit o ON o.id = u.org_id
                WHERE u.id = ?
                """, (rs, rowNum) -> new UserRow(rs.getLong("id"), rs.getLong("org_id"),
                rs.getString("username"), rs.getString("password_hash"),
                rs.getString("display_name"), rs.getBoolean("enabled"),
                rs.getString("org_name"), rs.getBoolean("org_enabled")), id)
                .stream().findFirst();
    }

    public List<String> roleCodes(long userId) {
        return jdbc.query("""
                SELECT r.code FROM user_role ur JOIN `role` r ON r.id = ur.role_id
                WHERE ur.user_id = ? ORDER BY r.code
                """, (rs, rowNum) -> rs.getString("code"), userId);
    }

    public record UserRow(long id, long orgId, String username, String passwordHash,
            String displayName, boolean enabled, String orgName, boolean orgEnabled) {
    }
}
