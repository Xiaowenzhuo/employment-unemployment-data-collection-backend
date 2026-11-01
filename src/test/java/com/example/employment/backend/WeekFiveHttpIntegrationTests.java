package com.example.employment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WeekFiveHttpIntegrationTests {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void correctionSummaryExportsAccountsAndVoidStayConsistent() throws Exception {
        seed();
        String province = login("w5_province");
        String enterprise = login("w5_enterprise");
        String base = "/api/v1/province/reports/95301";
        assertThat(call("GET", base + "/corrections", enterprise, null).statusCode()).isEqualTo(403);
        JsonNode before = data(call("GET", base + "/corrections", province, null));
        assertThat(before.path("effectiveForm").path("periodEmploymentCount").asInt()).isEqualTo(90);
        assertThat(before.path("currentVersion").asInt()).isEqualTo(4);
        assertThat(data(call("GET", "/api/v1/province/summaries?periodCode=W5_PERIOD", province, null))
                .path("periodEmploymentTotal").asInt()).isEqualTo(90);
        assertThat(call("POST", base + "/corrections", province,
                "{\"expectedVersion\":4,\"submissionVersion\":1,\"reason\":\"修订\",\"changes\":{\"baselineEmploymentCount\":99}}")
                .statusCode()).isEqualTo(400);
        JsonNode corrected = data(call("POST", base + "/corrections", province,
                "{\"expectedVersion\":4,\"submissionVersion\":1,\"reason\":\"修订\",\"changes\":{\"periodEmploymentCount\":85}}"));
        assertThat(corrected.path("currentVersion").asInt()).isEqualTo(5);
        assertThat(corrected.path("effectiveForm").path("periodEmploymentCount").asInt()).isEqualTo(85);
        assertThat(call("POST", base + "/corrections", province,
                "{\"expectedVersion\":4,\"submissionVersion\":1,\"reason\":\"旧版本\",\"changes\":{\"periodEmploymentCount\":80}}")
                .statusCode()).isEqualTo(409);
        JsonNode trace = data(call("GET", base + "/corrections", province, null));
        assertThat(trace.path("originalForm").path("periodEmploymentCount").asInt()).isEqualTo(90);
        assertThat(trace.path("corrections").size()).isEqualTo(1);
        assertThat(data(call("GET", "/api/v1/province/summaries?periodCode=W5_PERIOD", province, null))
                .path("netChange").asInt()).isEqualTo(-15);
        assertThat(data(call("GET", "/api/v1/province/data/reports?periodCode=W5_PERIOD", province, null))
                .path("items").get(0).path("effectiveForm").path("periodEmploymentCount").asInt())
                .isEqualTo(85);
        assertThat(data(call("GET", "/api/v1/province/data/enterprise-info?periodCode=W5_PERIOD", province, null))
                .path("items").get(0).path("filingSource").asText()).isEqualTo("UNAVAILABLE");
        assertThat(data(call("GET", "/api/v1/province/accounts?periodCode=W5_PERIOD", province, null))
                .path("total").asInt()).isEqualTo(1);
        assertThat(call("GET", "/api/v1/province/accounts?reportStatus=APPROVED_PROVINCE", province, null)
                .statusCode()).isEqualTo(400);
        for (String path : new String[] {"/api/v1/province/exports/reports?periodCode=W5_PERIOD",
                "/api/v1/province/exports/enterprise-info?periodCode=W5_PERIOD",
                "/api/v1/province/accounts/export?periodCode=W5_PERIOD"}) {
            var response = call("GET", path, province, null);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type").orElse(""))
                    .contains("spreadsheetml.sheet");
        }
        JsonNode voided = data(call("POST", base + "/void", province,
                "{\"expectedVersion\":5,\"submissionVersion\":1,\"reason\":\"重复记录\"}"));
        assertThat(voided.path("voided").asBoolean()).isTrue();
        assertThat(call("POST", base + "/void", province,
                "{\"expectedVersion\":5,\"submissionVersion\":1,\"reason\":\"重复\"}")
                .statusCode()).isEqualTo(409);
        assertThat(data(call("GET", "/api/v1/province/summaries?periodCode=W5_PERIOD", province, null))
                .path("enterpriseCount").asInt()).isZero();
        assertThat(data(call("GET", "/api/v1/province/data/reports?periodCode=W5_PERIOD", province, null))
                .path("total").asInt()).isZero();
        for (String filter : new String[] {"periodCode=W5_PERIOD", "month=2026-11",
                "periodCode=W5_PERIOD&reportStatus=APPROVED_PROVINCE"}) {
            assertThat(data(call("GET", "/api/v1/province/accounts?username=w5_enterprise&"
                    + filter, province, null)).path("total").asInt()).isZero();
        }
        assertThat(data(call("GET", "/api/v1/province/accounts?username=w5_enterprise",
                province, null)).path("total").asInt()).isEqualTo(1);
        var exportRequest = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                        + "/api/v1/province/accounts/export?username=w5_enterprise&periodCode=W5_PERIOD"))
                .header("Authorization", "Bearer " + province).GET().build();
        var export = client.send(exportRequest, HttpResponse.BodyHandlers.ofByteArray());
        assertThat(export.statusCode()).isEqualTo(200);
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(export.body()))) {
            assertThat(workbook.getSheetAt(0).getPhysicalNumberOfRows()).isEqualTo(1);
        }
        assertThat(data(call("GET", "/api/v1/province/reports?status=APPROVED_PROVINCE", province, null))
                .path("total").asInt()).isZero();
        assertThat(data(call("GET", "/api/v1/enterprise/reports/W5_PERIOD/history", enterprise, null))
                .path("voided").asBoolean()).isTrue();
        assertThat(data(call("GET", base + "/corrections", province, null))
                .path("voidEvent").path("reason").asText()).isEqualTo("重复记录");
    }

    private void seed() {
        jdbc.update("INSERT INTO region VALUES ('W5_P',NULL,'五周省',1)");
        jdbc.update("INSERT INTO region VALUES ('W5_C','W5_P','五周市',2)");
        jdbc.update("INSERT INTO org_unit VALUES (95100,NULL,'PROVINCE','W5_P','五周省',true)");
        jdbc.update("INSERT INTO org_unit VALUES (95200,95100,'CITY','W5_C','五周市',true)");
        jdbc.update("INSERT INTO org_unit VALUES (95300,95200,'ENTERPRISE','W5_C','五周企业',true)");
        user(95101, 95100, "w5_province", 3);
        user(95301, 95300, "w5_enterprise", 1);
        jdbc.update("""
                INSERT INTO survey_period (code,label,start_at,end_exclusive_at,demo,
                    province_org_id,open_at,close_at,current_version)
                VALUES ('W5_PERIOD','五周期次','2026-10-31 16:00:00','2026-11-30 16:00:00',
                    false,95100,'2026-10-31 16:00:00','2026-11-30 16:00:00',1)
                """);
        jdbc.update("""
                INSERT INTO enterprise_report (id,org_id,period_code,status,current_version,
                    submission_version,baseline_employment_count,period_employment_count,
                    other_reason,reduction_type_code,primary_reason_code,primary_reason_detail,
                    updated_at,submitted_at)
                VALUES (95301,95300,'W5_PERIOD','APPROVED_PROVINCE',4,1,100,90,
                    '无','TYPE_OTHER','REASON_OTHER','业务调整',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO report_snapshot (report_id,submission_version,
                    baseline_employment_count,period_employment_count,other_reason,
                    reduction_type_code,primary_reason_code,primary_reason_detail,
                    submitted_at,submitted_by,result)
                VALUES (95301,1,100,90,'无','TYPE_OTHER','REASON_OTHER','业务调整',
                    CURRENT_TIMESTAMP,95301,'SUBMITTED_CITY')
                """);
        jdbc.update("""
                INSERT INTO report_review_event (id,report_id,submission_version,level,action,
                    from_status,to_status,reviewer_user_id,reviewer_org_id,
                    reviewer_display_name,reviewed_at)
                VALUES (95301,95301,1,'CITY','APPROVE','SUBMITTED_CITY','SUBMITTED_PROVINCE',
                    95101,95100,'省级测试',CURRENT_TIMESTAMP)
                """);
    }
    private void user(long id, long org, String name, int role) {
        jdbc.update("""
                INSERT INTO user_account (id,org_id,username,password_hash,display_name,enabled,created_at)
                VALUES (?,?,?,?,?,true,CURRENT_TIMESTAMP)
                """, id, org, name, passwords.encode("testPass123"), name);
        jdbc.update("INSERT INTO user_role VALUES (?,?)", id, role);
    }
    private String login(String username) throws Exception {
        return data(call("POST", "/api/v1/auth/login", null,
                "{\"username\":\"" + username + "\",\"password\":\"testPass123\"}"))
                .path("accessToken").asText();
    }
    private HttpResponse<String> call(String method, String path, String token, String payload)
            throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (payload != null) builder.header("Content-Type", "application/json");
        builder.method(method, payload == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(payload));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode data(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).path("data");
    }
}
