package com.example.employment.backend.week78.shared;

import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AdminGuard {
    private static final Set<String> REQUIRED = Set.of(
            "PROVINCE.USER.READ", "PROVINCE.USER.CREATE", "PROVINCE.USER.UPDATE",
            "PROVINCE.USER.STATUS", "PROVINCE.USER.RESET_PASSWORD", "PROVINCE.USER.DELETE",
            "PROVINCE.ROLE.READ", "PROVINCE.ROLE.CREATE", "PROVINCE.ROLE.UPDATE",
            "PROVINCE.ROLE.DELETE");
    private final JdbcTemplate jdbc;

    public AdminGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lockProvince(long provinceId) {
        jdbc.queryForObject("SELECT id FROM org_unit WHERE id=? FOR UPDATE", Long.class, provinceId);
    }

    public void ensureOne(long provinceId) {
        List<Long> users = jdbc.query("""
                SELECT u.id FROM user_account u JOIN org_unit o ON o.id=u.org_id
                WHERE o.id=? AND o.org_type='PROVINCE' AND o.enabled=true
                  AND u.enabled=true AND u.deleted_at IS NULL
                """, (rs, row) -> rs.getLong(1), provinceId);
        for (long userId : users) {
            Set<String> permissions = Set.copyOf(jdbc.query("""
                    SELECT DISTINCT rp.permission_code FROM user_role ur
                    JOIN `role` r ON r.id=ur.role_id
                    JOIN role_permission rp ON rp.role_id=r.id
                    WHERE ur.user_id=? AND r.role_kind='FUNCTION' AND r.deleted_at IS NULL
                    """, (rs, row) -> rs.getString(1), userId));
            if (permissions.containsAll(REQUIRED)) return;
        }
        throw new AdminConflictException("LAST_ADMIN", "不能移走最后一个可用的省级管理员");
    }
}
