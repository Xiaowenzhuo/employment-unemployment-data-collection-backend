package com.example.employment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class WeekFourMigrationUpgradeTests {
    @Test
    void v4SnapshotSurvivesV5UpgradeWithoutInventedFilingVersion() {
        String url = "jdbc:h2:mem:w4upgrade_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        var database = new DriverManagerDataSource(url, "sa", "");
        Flyway.configure().dataSource(database).locations("classpath:db/migration")
                .target("4").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(database);
        jdbc.update("INSERT INTO region VALUES ('W4UP',NULL,'升级测试省',1)");
        jdbc.update("INSERT INTO org_unit VALUES (9400,NULL,'ENTERPRISE','W4UP','旧企业',true)");
        jdbc.update("""
                INSERT INTO user_account (id,org_id,username,password_hash,display_name,
                    enabled,created_at) VALUES (9401,9400,'w4_upgrade','unused','旧用户',true,CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO enterprise_report (id,org_id,period_code,status,current_version,
                    submission_version,updated_at,submitted_at)
                VALUES (9402,9400,'DEMO_2026_10','SUBMITTED_CITY',2,1,
                    CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO report_snapshot (report_id,submission_version,
                    baseline_employment_count,period_employment_count,other_reason,
                    submitted_at,submitted_by,result)
                VALUES (9402,1,100,90,'无',CURRENT_TIMESTAMP,9401,'SUBMITTED_CITY')
                """);

        Flyway.configure().dataSource(database).locations("classpath:db/migration").load().migrate();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_snapshot WHERE report_id=9402",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT filing_id FROM report_snapshot WHERE report_id=9402",
                Long.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT filing_version FROM report_snapshot WHERE report_id=9402",
                Integer.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM enterprise_report WHERE id=9402",
                String.class)).isEqualTo("SUBMITTED_CITY");
        assertThat(jdbc.queryForObject("SELECT next_id FROM id_allocator WHERE entity_name='report_review_event'",
                Long.class)).isEqualTo(1L);
    }
}
