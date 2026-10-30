package com.example.employment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
class WeekFourHttpIntegrationTests {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void cityProvinceReturnResubmitAndApproveAfterWindowCloses() throws Exception {
        seed();
        String enterprise = login("w4_enterprise");
        String city = login("w4_city");
        String otherCity = login("w4_other_city");
        String province = login("w4_province");
        String otherProvince = login("w4_other_province");

        assertThat(call("GET", "/api/v1/city/reports", null, null).statusCode()).isEqualTo(401);
        assertThat(call("GET", "/api/v1/city/reports", enterprise, null).statusCode()).isEqualTo(403);
        assertThat(call("GET", "/api/v1/city/reports?status=DRAFT", city, null).statusCode())
                .isEqualTo(400);
        assertThat(call("GET", "/api/v1/city/reports?page=no", city, null).statusCode())
                .isEqualTo(400);
        assertThat(call("GET", "/api/v1/city/reports?periodCode=UNKNOWN", city, null).statusCode())
                .isEqualTo(400);

        assertThat(call("PUT", "/api/v1/enterprise/reports/W4_OPEN", enterprise,
                form(0, 90)).statusCode()).isEqualTo(200);
        JsonNode submitted = data(call("POST", "/api/v1/enterprise/reports/W4_OPEN/submit",
                enterprise, "{\"expectedVersion\":1}"));
        String reportId = submitted.path("reportId").asText();
        assertThat(submitted.path("currentVersion").asInt()).isEqualTo(2);
        assertThat(data(call("GET", "/api/v1/city/reports", city, null))
                .path("total").asInt()).isEqualTo(1);
        assertThat(data(call("GET", "/api/v1/city/reports", otherCity, null))
                .path("total").asInt()).isZero();
        assertThat(call("GET", "/api/v1/city/reports/" + reportId, otherCity, null)
                .statusCode()).isEqualTo(404);
        assertThat(call("GET", "/api/v1/province/reports/" + reportId, province, null)
                .statusCode()).isEqualTo(404);
        assertThat(call("GET", "/api/v1/city/reports/abc", city, null).statusCode())
                .isEqualTo(400);
        JsonNode first = data(call("GET", "/api/v1/city/reports/" + reportId, city, null));
        assertThat(first.path("canReview").asBoolean()).isTrue();
        assertThat(first.path("form").path("periodEmploymentCount").asInt()).isEqualTo(90);
        assertThat(first.path("currentSnapshot").path("filingId").asText()).isEqualTo("8401");
        assertThat(first.path("currentSnapshot").path("filingVersion").asInt()).isEqualTo(1);
        assertThat(first.path("enterpriseName").asText()).isEqualTo("第四周备案企业");

        assertThat(call("POST", "/api/v1/city/reports/" + reportId + "/approve", city,
                "{\"expectedVersion\":2,\"submissionVersion\":1,\"comment\":\"x\"}")
                .statusCode()).isEqualTo(400);
        JsonNode cityReturn = data(call("POST", "/api/v1/city/reports/" + reportId + "/return",
                city, decision(2, 1, "   ")));
        assertThat(cityReturn.path("status").asText()).isEqualTo("RETURNED_CITY");
        assertThat(cityReturn.path("event").path("comment").isNull()).isTrue();
        assertThat(call("POST", "/api/v1/city/reports/" + reportId + "/return", city,
                decision(2, 1, "again")).statusCode()).isEqualTo(409);
        assertThat(data(call("GET", "/api/v1/enterprise/reports/W4_OPEN", enterprise, null))
                .path("latestReturn").path("level").asText()).isEqualTo("CITY");
        assertThat(data(call("GET", "/api/v1/enterprise/reports?status=RETURNED_CITY",
                enterprise, null)).path("items").get(0).path("latestReturn")
                .path("reviewerDisplayName").asText()).isEqualTo("w4_city");

        assertThat(call("PUT", "/api/v1/enterprise/reports/W4_OPEN", enterprise,
                form(3, 85)).statusCode()).isEqualTo(200);
        assertThat(data(call("GET", "/api/v1/city/reports/" + reportId, city, null))
                .path("form").path("periodEmploymentCount").asInt()).isEqualTo(90);
        assertThat(data(call("GET", "/api/v1/enterprise/reports/W4_OPEN", enterprise, null))
                .path("form").path("periodEmploymentCount").asInt()).isEqualTo(85);
        jdbc.update("UPDATE survey_period SET close_at='2021-01-01 00:00:00' WHERE code='W4_OPEN'");
        assertThat(periodItem(data(call("GET", "/api/v1/enterprise/periods", enterprise, null))
                .path("items"), "W4_OPEN").path("canWrite").asBoolean()).isTrue();
        assertThat(data(call("GET", "/api/v1/enterprise/reports/W4_OPEN", enterprise, null))
                .path("canWrite").asBoolean()).isTrue();
        JsonNode resubmitted = data(call("POST", "/api/v1/enterprise/reports/W4_OPEN/submit",
                enterprise, "{\"expectedVersion\":4}"));
        assertThat(resubmitted.path("submissionVersion").asInt()).isEqualTo(2);
        assertThat(data(call("GET", "/api/v1/enterprise/reports/W4_OPEN", enterprise, null))
                .path("latestReturn").isNull()).isTrue();
        assertThat(data(call("GET", "/api/v1/city/reports/" + reportId, city, null))
                .path("form").path("periodEmploymentCount").asInt()).isEqualTo(85);
        assertThat(call("POST", "/api/v1/city/reports/" + reportId + "/approve", city,
                decision(5, 1, null)).statusCode()).isEqualTo(409);
        assertThat(data(call("POST", "/api/v1/city/reports/" + reportId + "/approve", city,
                decision(5, 2, null))).path("status").asText()).isEqualTo("SUBMITTED_PROVINCE");
        assertThat(data(call("GET", "/api/v1/province/reports", province, null))
                .path("total").asInt()).isEqualTo(1);
        assertThat(call("GET", "/api/v1/province/reports/" + reportId, otherProvince, null)
                .statusCode()).isEqualTo(404);
        assertThat(data(call("POST", "/api/v1/province/reports/" + reportId + "/return", province,
                decision(6, 2, "  请补充说明  "))).path("event")
                .path("comment").asText()).isEqualTo("请补充说明");
        assertThat(data(call("GET", "/api/v1/enterprise/reports/W4_OPEN", enterprise, null))
                .path("latestReturn").path("level").asText()).isEqualTo("PROVINCE");
        assertThat(data(call("GET", "/api/v1/enterprise/reports/W4_OPEN", enterprise, null))
                .path("canWrite").asBoolean()).isTrue();
        assertThat(call("PUT", "/api/v1/enterprise/reports/W4_OPEN", enterprise,
                form(7, 80)).statusCode()).isEqualTo(200);
        assertThat(data(call("POST", "/api/v1/enterprise/reports/W4_OPEN/submit", enterprise,
                "{\"expectedVersion\":8}")).path("submissionVersion").asInt()).isEqualTo(3);
        assertThat(data(call("POST", "/api/v1/city/reports/" + reportId + "/approve", city,
                decision(9, 3, null))).path("status").asText()).isEqualTo("SUBMITTED_PROVINCE");
        assertThat(data(call("POST", "/api/v1/province/reports/" + reportId + "/approve", province,
                decision(10, 3, null))).path("status").asText()).isEqualTo("APPROVED_PROVINCE");
        assertThat(call("POST", "/api/v1/province/reports/" + reportId + "/approve", province,
                decision(10, 3, null)).statusCode()).isEqualTo(409);
        assertThat(data(call("GET", "/api/v1/enterprise/reports/W4_OPEN", enterprise, null))
                .path("canWrite").asBoolean()).isFalse();
        assertThat(call("PUT", "/api/v1/enterprise/reports/W4_OPEN", enterprise,
                form(11, 75)).statusCode()).isEqualTo(409);

        JsonNode history = data(call("GET", "/api/v1/enterprise/reports/W4_OPEN/history",
                enterprise, null));
        assertThat(history.path("snapshots").size()).isEqualTo(3);
        assertThat(history.path("reviewEvents").size()).isEqualTo(5);
        assertThat(history.path("snapshots").get(0).path("form")
                .path("periodEmploymentCount").asInt()).isEqualTo(80);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_review_event WHERE report_id=?",
                Integer.class, Long.parseLong(reportId))).isEqualTo(5);
    }

    private void seed() {
        jdbc.update("INSERT INTO region VALUES ('W4_P',NULL,'四周省',1)");
        jdbc.update("INSERT INTO region VALUES ('W4_C1','W4_P','四周一市',2)");
        jdbc.update("INSERT INTO region VALUES ('W4_C2','W4_P','四周二市',2)");
        jdbc.update("INSERT INTO region VALUES ('W4_OP',NULL,'外省',1)");
        jdbc.update("INSERT INTO org_unit VALUES (8100,NULL,'PROVINCE','W4_P','四周省',true)");
        jdbc.update("INSERT INTO org_unit VALUES (8200,8100,'CITY','W4_C1','四周一市',true)");
        jdbc.update("INSERT INTO org_unit VALUES (8250,8100,'CITY','W4_C2','四周二市',true)");
        jdbc.update("INSERT INTO org_unit VALUES (8300,8200,'ENTERPRISE','W4_C1','四周企业',true)");
        jdbc.update("INSERT INTO org_unit VALUES (9100,NULL,'PROVINCE','W4_OP','外省',true)");
        user(8101, 8100, "w4_province", 3);
        user(8201, 8200, "w4_city", 2);
        user(8251, 8250, "w4_other_city", 2);
        user(8301, 8300, "w4_enterprise", 1);
        user(9101, 9100, "w4_other_province", 3);
        jdbc.update("""
                INSERT INTO enterprise_filing (id,org_id,status,current_version,updated_at)
                VALUES (8401,8300,'APPROVED',1,CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO filing_version (filing_id,version,result,organization_code,
                    enterprise_name,nature_parent_code,nature_code,industry_parent_code,
                    industry_code,main_business,contact_name,address_parent_code,address_code,
                    postal_code,contact_phone,fax,submitted_at,submitted_by)
                VALUES (8401,1,'APPROVED','W4CODE001','第四周备案企业','DEMO_NATURE_1',
                    'DEMO_NATURE_1_1','DEMO_INDUSTRY_1','DEMO_INDUSTRY_1_1',
                    '测试业务','联系人','DEMO_ADDR_1','DEMO_ADDR_1_1','100000',
                    '13800000000','01000000000',CURRENT_TIMESTAMP,8301)
                """);
        jdbc.update("""
                INSERT INTO survey_period (code,label,start_at,end_exclusive_at,demo,
                    province_org_id,open_at,close_at,current_version)
                VALUES ('W4_OPEN','第四周期次','2020-01-01 00:00:00','2099-01-01 00:00:00',
                    false,8100,'2020-01-01 00:00:00','2099-01-01 00:00:00',1)
                """);
    }

    private void user(long id, long org, String name, int role) {
        jdbc.update("""
                INSERT INTO user_account (id,org_id,username,password_hash,display_name,enabled,created_at)
                VALUES (?,?,?,?,?,true,CURRENT_TIMESTAMP)
                """, id, org, name, passwords.encode("testPass123"), name);
        jdbc.update("INSERT INTO user_role VALUES (?,?)", id, role);
    }

    private String form(int version, int count) throws Exception {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("expectedVersion", version);
        input.put("baselineEmploymentCount", 100);
        input.put("periodEmploymentCount", count);
        input.put("otherReason", "无");
        input.put("reductionTypeCode", "TYPE_NATURAL_ATTRITION");
        input.put("primaryReasonCode", "REASON_VOLUNTARY_LEAVE");
        input.put("primaryReasonDetail", "员工自行离职");
        input.put("secondaryReasonCode", null);
        input.put("secondaryReasonDetail", null);
        input.put("tertiaryReasonCode", null);
        input.put("tertiaryReasonDetail", null);
        return json.writeValueAsString(input);
    }

    private String decision(int version, int submission, String comment) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("expectedVersion", version);
        body.put("submissionVersion", submission);
        if (comment != null) body.put("comment", comment);
        return json.writeValueAsString(body);
    }

    private String login(String username) throws Exception {
        var response = call("POST", "/api/v1/auth/login", null,
                json.writeValueAsString(Map.of("username", username, "password", "testPass123")));
        assertThat(response.statusCode()).isEqualTo(200);
        return data(response).path("accessToken").asText();
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

    private JsonNode data(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return json.readTree(response.body()).path("data");
    }

    private JsonNode periodItem(JsonNode items, String code) {
        for (JsonNode item : items) {
            if (code.equals(item.path("code").asText())) return item;
        }
        throw new AssertionError("Missing period " + code);
    }
}
