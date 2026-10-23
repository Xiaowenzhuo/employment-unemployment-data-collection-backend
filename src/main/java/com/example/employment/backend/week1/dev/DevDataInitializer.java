package com.example.employment.backend.week1.dev;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevDataInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;

    public DevDataInitializer(JdbcTemplate jdbc, PasswordEncoder passwords) {
        this.jdbc = jdbc;
        this.passwords = passwords;
    }

    @Override
    public void run(ApplicationArguments args) {
        String enterprisePassword = required("APP_DEMO_ENTERPRISE_PASSWORD");
        String cityPassword = required("APP_DEMO_CITY_PASSWORD");
        String provincePassword = required("APP_DEMO_PROVINCE_PASSWORD");

        region("DEMO_P", null, "演示省", 1);
        region("DEMO_C", "DEMO_P", "演示市", 2);
        region("DEMO_D", "DEMO_C", "演示区县", 3);
        organization(100, null, "PROVINCE", "DEMO_P", "演示省级单位");
        organization(200, 100L, "CITY", "DEMO_C", "演示市级单位");
        organization(300, 200L, "ENTERPRISE", "DEMO_D", "演示企业");
        user(101, 300, "demo_enterprise", "演示企业用户", enterprisePassword, 1);
        user(102, 200, "demo_city", "演示市级用户", cityPassword, 2);
        user(103, 100, "demo_province", "演示省级用户", provincePassword, 3);
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set for the dev profile");
        }
        return value;
    }

    private boolean exists(String table, String keyColumn, Object key) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + keyColumn + " = ?", Integer.class, key);
        return count != null && count > 0;
    }

    private void region(String code, String parent, String name, int level) {
        if (!exists("region", "code", code)) {
            jdbc.update("INSERT INTO region (code, parent_code, name, level) VALUES (?, ?, ?, ?)",
                    code, parent, name, level);
        }
    }

    private void organization(long id, Long parent, String type, String region, String name) {
        if (!exists("org_unit", "id", id)) {
            jdbc.update("""
                    INSERT INTO org_unit (id, parent_id, org_type, region_code, name, enabled)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, id, parent, type, region, name, true);
        }
    }

    private void user(long id, long orgId, String username, String displayName,
            String password, long roleId) {
        if (!exists("user_account", "username", username)) {
            jdbc.update("""
                    INSERT INTO user_account
                    (id, org_id, username, password_hash, display_name, enabled, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, id, orgId, username, passwords.encode(password), displayName,
                    true, LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
            jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", id, roleId);
        }
    }
}
