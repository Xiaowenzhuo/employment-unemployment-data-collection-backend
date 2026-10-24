package com.example.employment.backend.week2.account.dao;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AccountDao {
    private final JdbcTemplate jdbc;

    public AccountDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean usernameExists(String username) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE username = ?",
                Integer.class, username);
        return count != null && count > 0;
    }

    public long allocate(String entity) {
        Long next = jdbc.queryForObject(
                "SELECT next_id FROM id_allocator WHERE entity_name = ? FOR UPDATE", Long.class, entity);
        if (next == null) {
            throw new IllegalStateException("Missing ID allocator: " + entity);
        }
        jdbc.update("UPDATE id_allocator SET next_id = ? WHERE entity_name = ?", next + 1, entity);
        return next;
    }

    public void create(long orgId, long cityId, String regionCode, String orgName,
            long userId, String username, String passwordHash, LocalDateTime now) {
        jdbc.update("""
                INSERT INTO org_unit (id, parent_id, org_type, region_code, name, enabled)
                VALUES (?, ?, 'ENTERPRISE', ?, ?, true)
                """, orgId, cityId, regionCode, orgName);
        jdbc.update("""
                INSERT INTO user_account
                (id, org_id, username, password_hash, display_name, enabled, created_at)
                VALUES (?, ?, ?, ?, ?, true, ?)
                """, userId, orgId, username, passwordHash, orgName, now);
        int assigned = jdbc.update("""
                INSERT INTO user_role (user_id, role_id)
                SELECT ?, id FROM `role` WHERE code = 'ENTERPRISE'
                """, userId);
        if (assigned != 1) {
            throw new IllegalStateException("Enterprise role is missing");
        }
    }

    public void audit(long actorId, long orgId, LocalDateTime now) {
        jdbc.update("""
                INSERT INTO audit_event (event_id, actor_user_id, action, target_org_id, occurred_at)
                VALUES (?, ?, 'ACCOUNT_CREATED', ?, ?)
                """, UUID.randomUUID().toString(), actorId, orgId, now);
    }
}
