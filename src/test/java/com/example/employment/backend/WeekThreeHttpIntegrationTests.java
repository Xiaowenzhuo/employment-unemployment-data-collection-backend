package com.example.employment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WeekThreeHttpIntegrationTests {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void periodReportSubmissionScopeAndHistory() throws Exception {
        seed();
        String province = login("w3_province");
        String otherProvince = login("w3_other_province");
        String enterprise = login("w3_enterprise");
        String unfiled = login("w3_unfiled");
        String otherEnterprise = login("w3_other_enterprise");

        assertThat(call("GET", "/api/v1/province/periods", null, null).statusCode()).isEqualTo(401);
        assertThat(call("GET", "/api/v1/province/periods", enterprise, null).statusCode()).isEqualTo(403);
        assertThat(call("POST", "/api/v1/province/periods", province,
                period("W3_BAD", "2030-01-01T00:00:00Z", "2029-01-01T00:00:00Z"))
                .statusCode()).isEqualTo(400);

        var future = call("POST", "/api/v1/province/periods", province,
                period("W3_FUTURE", "2098-01-01T00:00:00Z", "2099-01-01T00:00:00Z"));
        assertThat(future.statusCode()).isEqualTo(201);
        assertThat(body(future).path("data").path("currentVersion").asInt()).isEqualTo(1);
        assertThat(call("POST", "/api/v1/province/periods", province,
                period("W3_FUTURE", "2098-01-01T00:00:00Z", "2099-01-01T00:00:00Z"))
                .statusCode()).isEqualTo(409);
        var edited = call("PUT", "/api/v1/province/periods/W3_FUTURE", province,
                updatePeriod(1, "2098-02-01T00:00:00Z", "2099-02-01T00:00:00Z"));
        assertThat(edited.statusCode()).isEqualTo(200);
        assertThat(body(edited).path("data").path("currentVersion").asInt()).isEqualTo(2);
        assertThat(call("PUT", "/api/v1/province/periods/W3_FUTURE", province,
                updatePeriod(1, "2098-02-01T00:00:00Z", "2099-02-01T00:00:00Z"))
                .statusCode()).isEqualTo(409);
        assertThat(call("PUT", "/api/v1/province/periods/DEMO_2026_10", province,
                updatePeriod(1, "2098-02-01T00:00:00Z", "2099-02-01T00:00:00Z"))
                .statusCode()).isEqualTo(409);
        assertThat(call("PUT", "/api/v1/enterprise/reports/W3_FUTURE", enterprise,
                form(0, 120, 115, false)).statusCode()).isEqualTo(409);

        assertThat(call("POST", "/api/v1/province/periods", province,
                period("W3_OPEN", "2020-01-01T00:00:00Z", "2099-01-01T00:00:00Z"))
                .statusCode()).isEqualTo(201);
        assertThat(call("POST", "/api/v1/province/periods", province,
                period("W3_SECOND", "2020-01-01T00:00:00Z", "2099-01-01T00:00:00Z"))
                .statusCode()).isEqualTo(201);
        assertThat(call("POST", "/api/v1/province/periods", otherProvince,
                period("W3_FOREIGN", "2020-01-01T00:00:00Z", "2099-01-01T00:00:00Z"))
                .statusCode()).isEqualTo(201);
        assertThat(call("PUT", "/api/v1/province/periods/W3_FOREIGN", province,
                updatePeriod(1, "2020-01-01T00:00:00Z", "2099-01-01T00:00:00Z"))
                .statusCode()).isEqualTo(404);
        assertThat(call("GET", "/api/v1/enterprise/reports/W3_FOREIGN", enterprise, null)
                .statusCode()).isEqualTo(404);
        assertThat(call("GET", "/api/v1/enterprise/reports?periodCode=W3_FOREIGN", enterprise, null)
                .statusCode()).isEqualTo(400);
        assertThat(call("GET", "/api/v1/province/filings?periodCode=W3_FOREIGN", province, null)
                .statusCode()).isEqualTo(400);
        assertThat(call("GET", "/api/v1/province/filings/periods", province, null).body())
                .doesNotContain("W3_FOREIGN");
        assertThat(call("GET", "/api/v1/province/periods", province, null).body())
                .doesNotContain("W3_FOREIGN");
        assertThat(call("GET", "/api/v1/enterprise/periods", enterprise, null).body())
                .doesNotContain("W3_FOREIGN");
        assertThat(body(call("GET", "/api/v1/enterprise/reports/options", enterprise, null))
                .path("data").path("reductionTypes").toString()).contains("TYPE_NATURAL_ATTRITION");
        assertThat(body(call("GET", "/api/v1/enterprise/reports/options", enterprise, null))
                .path("data").path("reasons").toString()).contains("REASON_VOLUNTARY_LEAVE");
        assertThat(call("PUT", "/api/v1/enterprise/reports/W3_OPEN", unfiled,
                form(0, 120, 115, true)).statusCode()).isEqualTo(403);
        assertThat(call("PUT", "/api/v1/enterprise/reports/DEMO_2026_10", enterprise,
                form(0, 120, 115, true)).statusCode()).isEqualTo(409);
        assertThat(call("POST", "/api/v1/enterprise/reports/W3_OPEN/submit", enterprise,
                "{\"expectedVersion\":1}").statusCode()).isEqualTo(409);

        var empty = body(call("GET", "/api/v1/enterprise/reports/W3_OPEN", enterprise, null))
                .path("data");
        assertThat(empty.path("currentVersion").asInt()).isZero();
        assertThat(empty.path("form").isNull()).isTrue();
        assertThat(call("PUT", "/api/v1/enterprise/reports/W3_OPEN", enterprise,
                form(0, 120, 115, false)).statusCode()).isEqualTo(200);
        var missing = call("POST", "/api/v1/enterprise/reports/W3_OPEN/submit", enterprise,
                "{\"expectedVersion\":1}");
        assertThat(missing.statusCode()).isEqualTo(400);
        assertThat(body(missing).path("data").path("fieldErrors").path("reductionTypeCode").asText())
                .isNotBlank();
        assertThat(call("PUT", "/api/v1/enterprise/reports/W3_OPEN", enterprise,
                form(0, 120, 115, true)).statusCode()).isEqualTo(409);
        assertThat(call("PUT", "/api/v1/enterprise/reports/W3_OPEN", enterprise,
                form(1, 120, 115, true).replace("\"expectedVersion\":1",
                        "\"expectedVersion\":1,\"orgId\":6300")).statusCode()).isEqualTo(400);
        assertThat(call("PUT", "/api/v1/enterprise/reports/W3_OPEN", enterprise,
                form(1, 120, 115, true).replace("\"secondaryReasonCode\":null",
                        "\"secondaryReasonCode\":\"REASON_JOB_TRANSFER\"")).statusCode())
                .isEqualTo(400);
        var invalid = form(1, 120, 115, true).replace("REASON_VOLUNTARY_LEAVE", "REASON_FUNDING");
        assertThat(call("PUT", "/api/v1/enterprise/reports/W3_OPEN", enterprise, invalid)
                .statusCode()).isEqualTo(400);
        var saved = call("PUT", "/api/v1/enterprise/reports/W3_OPEN", enterprise,
                form(1, 120, 115, true));
        assertThat(saved.statusCode()).isEqualTo(200);
        assertThat(body(saved).path("data").path("currentVersion").asInt()).isEqualTo(2);
        var submitted = call("POST", "/api/v1/enterprise/reports/W3_OPEN/submit", enterprise,
                "{\"expectedVersion\":2}");
        assertThat(submitted.statusCode()).isEqualTo(200);
        assertThat(body(submitted).path("data").path("status").asText()).isEqualTo("SUBMITTED_CITY");
        assertThat(body(submitted).path("data").path("submissionVersion").asInt()).isEqualTo(1);
        assertThat(call("POST", "/api/v1/enterprise/reports/W3_OPEN/submit", enterprise,
                "{\"expectedVersion\":3}").statusCode()).isEqualTo(409);
        assertThat(call("PUT", "/api/v1/enterprise/reports/W3_OPEN", enterprise,
                form(3, 120, 115, true)).statusCode()).isEqualTo(409);
        assertThat(body(call("GET", "/api/v1/enterprise/reports/W3_OPEN/history", enterprise, null))
                .path("data").path("snapshots").get(0).path("form")
                .path("periodEmploymentCount").asInt()).isEqualTo(115);
        assertThat(call("GET", "/api/v1/enterprise/reports/W3_OPEN/history", otherEnterprise, null)
                .statusCode()).isEqualTo(404);
        assertThat(call("PUT", "/api/v1/province/periods/W3_OPEN", province,
                updatePeriod(1, "2020-01-01T00:00:00Z", "2099-01-01T00:00:00Z"))
                .statusCode()).isEqualTo(409);

        var next = body(call("GET", "/api/v1/enterprise/reports/W3_SECOND", enterprise, null))
                .path("data");
        assertThat(next.path("baselineLocked").asBoolean()).isTrue();
        assertThat(next.path("lockedBaselineEmploymentCount").asInt()).isEqualTo(120);
        assertThat(call("PUT", "/api/v1/enterprise/reports/W3_SECOND", enterprise,
                form(0, 121, 121, false)).statusCode()).isEqualTo(400);
        assertThat(call("PUT", "/api/v1/enterprise/reports/W3_SECOND", enterprise,
                form(0, 120, 120, false).replace("\"reductionTypeCode\":null",
                        "\"reductionTypeCode\":\"TYPE_OTHER\"")).statusCode()).isEqualTo(400);
        assertThat(call("PUT", "/api/v1/enterprise/reports/W3_SECOND", enterprise,
                form(0, 120, 120, false).replace("\"otherReason\":\"无\"",
                        "\"otherReason\":\" \"")).statusCode()).isEqualTo(200);
        var missingOther = call("POST", "/api/v1/enterprise/reports/W3_SECOND/submit", enterprise,
                "{\"expectedVersion\":1}");
        assertThat(missingOther.statusCode()).isEqualTo(400);
        assertThat(body(missingOther).path("data").path("fieldErrors").path("otherReason").asText())
                .isNotBlank();
        assertThat(call("PUT", "/api/v1/enterprise/reports/W3_SECOND", enterprise,
                form(1, 120, 120, false)).statusCode()).isEqualTo(200);
        assertThat(call("POST", "/api/v1/enterprise/reports/W3_SECOND/submit", enterprise,
                "{\"expectedVersion\":2}").statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT employment_count FROM enterprise_baseline WHERE org_id=6300",
                Integer.class)).isEqualTo(120);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM report_snapshot s
                JOIN enterprise_report r ON r.id=s.report_id WHERE r.org_id=6300
                """, Integer.class))
                .isEqualTo(2);
        assertThat(body(call("GET", "/api/v1/enterprise/reports?status=SUBMITTED_CITY", enterprise,
                null)).path("data").path("total").asInt()).isEqualTo(2);
        assertThat(call("GET", "/api/v1/enterprise/reports?status=BAD", enterprise, null)
                .statusCode()).isEqualTo(400);
        assertThat(call("GET", "/api/v1/enterprise/reports?unknown=1", enterprise, null)
                .statusCode()).isEqualTo(400);
        var badPage = call("GET", "/api/v1/enterprise/reports?page=abc", enterprise, null);
        assertThat(badPage.statusCode()).isEqualTo(400);
        assertThat(body(badPage).path("data").path("fieldErrors").path("page").asText())
                .isNotBlank();
    }

    private void seed() {
        jdbc.update("INSERT INTO region VALUES ('W3_P',NULL,'三周省',1)");
        jdbc.update("INSERT INTO region VALUES ('W3_C','W3_P','三周市',2)");
        jdbc.update("INSERT INTO region VALUES ('W3_OTHER_P',NULL,'外省',1)");
        jdbc.update("INSERT INTO region VALUES ('W3_OTHER_C','W3_OTHER_P','外市',2)");
        jdbc.update("INSERT INTO org_unit VALUES (6100,NULL,'PROVINCE','W3_P','省级',true)");
        jdbc.update("INSERT INTO org_unit VALUES (6200,6100,'CITY','W3_C','市级',true)");
        jdbc.update("INSERT INTO org_unit VALUES (6300,6200,'ENTERPRISE','W3_C','企业',true)");
        jdbc.update("INSERT INTO org_unit VALUES (6400,6200,'ENTERPRISE','W3_C','未备案企业',true)");
        jdbc.update("INSERT INTO org_unit VALUES (7100,NULL,'PROVINCE','W3_OTHER_P','外省级',true)");
        jdbc.update("INSERT INTO org_unit VALUES (7200,7100,'CITY','W3_OTHER_C','外市级',true)");
        jdbc.update("INSERT INTO org_unit VALUES (7300,7200,'ENTERPRISE','W3_OTHER_C','外企业',true)");
        user(6101, 6100, "w3_province", 3);
        user(6301, 6300, "w3_enterprise", 1);
        user(6401, 6400, "w3_unfiled", 1);
        user(7101, 7100, "w3_other_province", 3);
        user(7301, 7300, "w3_other_enterprise", 1);
        jdbc.update("""
                INSERT INTO enterprise_filing (id,org_id,status,current_version,updated_at)
                VALUES (9001,6300,'APPROVED',1,CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO filing_version (filing_id,version,result,organization_code,
                    enterprise_name,nature_parent_code,nature_code,industry_parent_code,
                    industry_code,main_business,contact_name,address_parent_code,address_code,
                    postal_code,contact_phone,fax,submitted_at,submitted_by)
                VALUES (9001,1,'APPROVED','W3CODE001','企业','DEMO_NATURE_1',
                    'DEMO_NATURE_1_1','DEMO_INDUSTRY_1','DEMO_INDUSTRY_1_1',
                    '测试业务','联系人','DEMO_ADDR_1','DEMO_ADDR_1_1','100000',
                    '13800000000','01000000000',CURRENT_TIMESTAMP,6301)
                """);
    }

    private void user(long id, long org, String name, long role) {
        jdbc.update("""
                INSERT INTO user_account (id,org_id,username,password_hash,display_name,enabled,created_at)
                VALUES (?,?,?,?,?,true,CURRENT_TIMESTAMP)
                """, id, org, name, passwords.encode("testPass123"), name);
        jdbc.update("INSERT INTO user_role VALUES (?,?)", id, role);
    }

    private String period(String code, String open, String close) throws Exception {
        return json.writeValueAsString(Map.of("code", code, "label", "三周测试期次",
                "startAt", "2026-01-01T00:00:00Z", "endExclusiveAt", "2027-01-01T00:00:00Z",
                "openAt", open, "closeAt", close, "demo", true));
    }

    private String updatePeriod(int version, String open, String close) throws Exception {
        return json.writeValueAsString(Map.of("expectedVersion", version, "label", "修改后的期次",
                "startAt", "2026-01-01T00:00:00Z", "endExclusiveAt", "2027-01-01T00:00:00Z",
                "openAt", open, "closeAt", close));
    }

    private String form(int version, Integer baseline, Integer current, boolean complete) throws Exception {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("expectedVersion", version);
        input.put("baselineEmploymentCount", baseline);
        input.put("periodEmploymentCount", current);
        input.put("otherReason", "无");
        input.put("reductionTypeCode", complete ? "TYPE_NATURAL_ATTRITION" : null);
        input.put("primaryReasonCode", complete ? "REASON_VOLUNTARY_LEAVE" : null);
        input.put("primaryReasonDetail", complete ? "员工自行离职" : null);
        input.put("secondaryReasonCode", null);
        input.put("secondaryReasonDetail", null);
        input.put("tertiaryReasonCode", null);
        input.put("tertiaryReasonDetail", null);
        if (current != null && baseline != null && current >= baseline) {
            input.put("reductionTypeCode", null);
            input.put("primaryReasonCode", null);
            input.put("primaryReasonDetail", null);
        }
        return json.writeValueAsString(input);
    }

    private String login(String username) throws Exception {
        var response = call("POST", "/api/v1/auth/login", null,
                json.writeValueAsString(Map.of("username", username, "password", "testPass123")));
        assertThat(response.statusCode()).isEqualTo(200);
        return body(response).path("data").path("accessToken").asText();
    }

    private HttpResponse<String> call(String method, String path, String token, String payload)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (payload != null) request.header("Content-Type", "application/json");
        request.method(method, payload == null ? HttpRequest.BodyPublishers.noBody() :
                HttpRequest.BodyPublishers.ofString(payload));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode body(HttpResponse<String> response) { return json.readTree(response.body()); }
}
