package com.example.employment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class TeacherRevisionMigrationUpgradeTests {
    @Test
    void v8ReportsGainRollingCurrentBaselineWithoutChangingOldSnapshots() {
        String url="jdbc:h2:mem:revision_"+UUID.randomUUID().toString().replace("-","")
                +";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        var database=new DriverManagerDataSource(url,"sa","");
        Flyway.configure().dataSource(database).locations("classpath:db/migration")
                .target("8").load().migrate();
        JdbcTemplate jdbc=new JdbcTemplate(database);
        jdbc.update("INSERT INTO region VALUES ('RUP',NULL,'升级省',1)");
        jdbc.update("INSERT INTO region VALUES ('RUC','RUP','升级市',2)");
        jdbc.update("INSERT INTO org_unit VALUES (99500,NULL,'PROVINCE','RUP','升级省',true)");
        jdbc.update("INSERT INTO org_unit VALUES (99501,99500,'CITY','RUC','升级市',true)");
        jdbc.update("INSERT INTO org_unit VALUES (99502,99501,'ENTERPRISE','RUC','升级企业',true)");
        jdbc.update("INSERT INTO user_account (id,org_id,username,password_hash,display_name,"
                +"enabled,created_at) VALUES (99503,99502,'revision_upgrade','unused',"
                +"'升级用户',true,CURRENT_TIMESTAMP)");
        for (int i=1;i<=3;i++) {
            String code="RUP_P"+i;
            jdbc.update("""
                    INSERT INTO survey_period (code,label,start_at,end_exclusive_at,demo,
                        province_org_id,open_at,close_at,current_version)
                    VALUES (?,?,?,?,false,99500,'2020-01-01 00:00:00',
                        '2029-01-01 00:00:00',1)
                    """,code,code,"2026-0"+i+"-01 00:00:00","2026-0"+(i+1)+"-01 00:00:00");
            jdbc.update("""
                    INSERT INTO enterprise_report (id,org_id,period_code,status,current_version,
                        submission_version,baseline_employment_count,period_employment_count,
                        other_reason,updated_at,submitted_at,voided,voided_at)
                    VALUES (?,99502,?,'APPROVED_PROVINCE',4,1,500,?,'无',
                        CURRENT_TIMESTAMP,?, ?, ?)
                    """,99510+i,code,110-i*10,"2026-0"+i+"-02 00:00:00",
                    i==1,i==1?"2026-02-15 00:00:00":null);
            jdbc.update("""
                    INSERT INTO report_snapshot (report_id,submission_version,
                        baseline_employment_count,period_employment_count,other_reason,
                        submitted_at,submitted_by,result)
                    VALUES (?,1,500,?,'无',?,99503,'SUBMITTED_CITY')
                    """,99510+i,110-i*10,"2026-0"+i+"-02 00:00:00");
        }
        Flyway.configure().dataSource(database).locations("classpath:db/migration")
                .load().migrate();
        assertThat(jdbc.queryForObject("SELECT baseline_employment_count FROM enterprise_report "
                +"WHERE id=99512",Integer.class)).isEqualTo(100);
        assertThat(jdbc.queryForObject("SELECT baseline_source_period_code FROM enterprise_report "
                +"WHERE id=99512",String.class)).isEqualTo("RUP_P1");
        assertThat(jdbc.queryForObject("SELECT baseline_employment_count FROM enterprise_report "
                +"WHERE id=99513",Integer.class)).isEqualTo(90);
        assertThat(jdbc.queryForObject("SELECT baseline_source_period_code FROM enterprise_report "
                +"WHERE id=99513",String.class)).isEqualTo("RUP_P2");
        assertThat(jdbc.queryForObject("SELECT baseline_employment_count FROM report_snapshot "
                +"WHERE report_id=99512",Integer.class)).isEqualTo(500);
        assertThat(jdbc.queryForObject("SELECT baseline_source_period_code FROM report_snapshot "
                +"WHERE report_id=99512",String.class)).isNull();
    }
}
