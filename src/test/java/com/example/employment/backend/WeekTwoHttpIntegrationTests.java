package com.example.employment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WeekTwoHttpIntegrationTests {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void fullFilingFlowAndAccessRules() throws Exception {
        seed();
        String province = login("w2_province", "testPass123");
        String city = login("w2_city", "testPass123");
        String otherProvince = login("w2_other_province", "testPass123");

        var preflight = request("OPTIONS", "/api/v1/enterprise/profile", null, null,
                Map.of("Origin", "http://localhost:5173", "Access-Control-Request-Method", "PUT"));
        assertThat(preflight.statusCode()).isEqualTo(200);
        assertThat(preflight.headers().firstValue("Access-Control-Allow-Methods").orElse(""))
                .contains("PUT");

        assertThat(request("POST", "/api/v1/province/enterprise-accounts", null,
                account("w2_first", "W2_D", "演示企业甲"), Map.of()).statusCode()).isEqualTo(401);
        assertThat(request("POST", "/api/v1/province/enterprise-accounts", city,
                account("w2_first", "W2_D", "演示企业甲"), Map.of()).statusCode()).isEqualTo(403);
        var invalidRegion = request("POST", "/api/v1/province/enterprise-accounts", province,
                account("w2_bad", "MISSING", "坏地区"), Map.of());
        assertThat(invalidRegion.statusCode()).isEqualTo(400);
        assertThat(body(invalidRegion).path("data").path("fieldErrors").path("regionCode").asText())
                .isNotBlank();
        assertThat(request("POST", "/api/v1/province/enterprise-accounts", province,
                account("w2_bad", "W2_C2", "无市局"), Map.of()).statusCode()).isEqualTo(400);
        assertThat(request("POST", "/api/v1/province/enterprise-accounts", province,
                account("w2_bad", "W2_OTHER_C", "越界"), Map.of()).statusCode()).isEqualTo(403);

        var created = request("POST", "/api/v1/province/enterprise-accounts", province,
                account("w2_first", "W2_D", "演示企业甲"), Map.of());
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).doesNotContain("testPass123", "passwordHash");
        String orgId = body(created).path("data").path("orgId").asText();
        assertThat(body(created).path("data").path("region").path("code").asText())
                .isEqualTo("W2_D");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_role ur JOIN user_account u " +
                "ON u.id=ur.user_id WHERE u.username='w2_first'", Integer.class)).isEqualTo(1);
        for (int length : new int[] {80, 81, 120}) {
            String name = "A".repeat(length);
            String username = "w2_long" + length;
            var longName = request("POST", "/api/v1/province/enterprise-accounts", province,
                    account(username, "W2_D", name), Map.of());
            assertThat(longName.statusCode()).isEqualTo(201);
            assertThat(body(longName).path("data").path("orgName").asText()).isEqualTo(name);
            var me = body(request("GET", "/api/v1/auth/me",
                    login(username, "testPass123"), null, Map.of())).path("data").path("user");
            assertThat(me.path("orgName").asText()).isEqualTo(name);
            assertThat(me.path("displayName").asText()).hasSize(Math.min(length, 80));
        }
        var tooLongName = request("POST", "/api/v1/province/enterprise-accounts", province,
                account("w2_long121", "W2_D", "A".repeat(121)), Map.of());
        assertThat(tooLongName.statusCode()).isEqualTo(400);
        assertThat(body(tooLongName).path("data").path("fieldErrors").path("orgName").asText())
                .isNotBlank();
        assertThat(request("POST", "/api/v1/province/enterprise-accounts", province,
                account("w2_first", "W2_D", "重复"), Map.of()).statusCode()).isEqualTo(409);
        assertThat(request("POST", "/api/v1/province/enterprise-accounts", province,
                account("w2_second", "W2_D", "演示企业乙"), Map.of()).statusCode()).isEqualTo(201);

        String enterprise = login("w2_first", "testPass123");
        String second = login("w2_second", "testPass123");
        assertThat(body(request("GET", "/api/v1/enterprise/profile", enterprise, null, Map.of()))
                .path("data").path("status").isNull()).isTrue();
        assertThat(body(request("GET", "/api/v1/enterprise/filing", enterprise, null, Map.of()))
                .path("data").path("canReport").asBoolean()).isFalse();
        assertThat(body(request("GET", "/api/v1/enterprise/filing/options", enterprise, null, Map.of()))
                .path("data").path("natures").get(0).path("children").get(0)
                .path("code").asText()).isEqualTo("DEMO_NATURE_1_1");
        assertThat(request("GET", "/api/v1/enterprise/profile", province, null, Map.of())
                .statusCode()).isEqualTo(403);
        assertThat(request("GET", "/api/v1/province/filings", enterprise, null, Map.of())
                .statusCode()).isEqualTo(403);
        assertThat(request("PUT", "/api/v1/enterprise/profile", enterprise,
                "{\"regionCode\":\"W2_OTHER_C\"}", Map.of()).statusCode()).isEqualTo(400);

        var partial = request("PUT", "/api/v1/enterprise/profile", enterprise,
                "{\"organizationCode\":\"AB1234567\"}", Map.of());
        assertThat(partial.statusCode()).isEqualTo(200);
        String filingId = body(partial).path("data").path("filingId").asText();
        assertThat(filingId).isNotBlank();
        var incomplete = request("POST", "/api/v1/enterprise/filing/submit", enterprise,
                "{\"expectedVersion\":0}", Map.of());
        assertThat(incomplete.statusCode()).isEqualTo(400);
        assertThat(body(incomplete).path("data").path("fieldErrors").path("enterpriseName").asText())
                .isNotBlank();
        var invalidPhone = request("PUT", "/api/v1/enterprise/profile", enterprise,
                profile().replace("0871-12345678", "bad-phone"), Map.of());
        assertThat(invalidPhone.statusCode()).isEqualTo(400);
        assertThat(body(invalidPhone).path("data").path("fieldErrors").path("contactPhone").asText())
                .isNotBlank();
        var invalidOption = request("PUT", "/api/v1/enterprise/profile", enterprise,
                profile().replace("DEMO_NATURE_1_1", "DEMO_INDUSTRY_1_1"), Map.of());
        assertThat(invalidOption.statusCode()).isEqualTo(400);
        assertThat(body(invalidOption).path("data").path("fieldErrors").path("natureCode").asText())
                .isNotBlank();
        assertThat(request("PUT", "/api/v1/enterprise/profile", enterprise, profile(), Map.of())
                .statusCode()).isEqualTo(200);
        assertThat(request("PUT", "/api/v1/enterprise/profile", second,
                "{\"organizationCode\":\"ab1234567\"}", Map.of()).statusCode()).isEqualTo(409);
        assertThat(request("GET", "/api/v1/enterprise/filing/versions/1", second,
                null, Map.of()).statusCode()).isEqualTo(404);

        var submitted = request("POST", "/api/v1/enterprise/filing/submit", enterprise,
                "{\"expectedVersion\":0}", Map.of());
        assertThat(submitted.statusCode()).isEqualTo(200);
        assertThat(body(submitted).path("data").path("currentVersion").asInt()).isEqualTo(1);
        String submittedAt = body(submitted).path("data").path("submittedAt").asText();
        assertThat(body(request("GET", "/api/v1/enterprise/filing", enterprise, null,
                Map.of())).path("data").path("submittedAt").asText()).isEqualTo(submittedAt);
        for (String invalid : new String[] {"0", "-1", "abc"}) {
            assertThat(request("GET", "/api/v1/enterprise/filing/versions/" + invalid,
                    enterprise, null, Map.of()).statusCode()).isEqualTo(400);
            assertThat(request("GET", "/api/v1/province/filings/" + filingId +
                    "/versions/" + invalid, province, null, Map.of()).statusCode()).isEqualTo(400);
        }
        assertThat(request("GET", "/api/v1/enterprise/filing/versions/99", enterprise,
                null, Map.of()).statusCode()).isEqualTo(404);
        assertThat(request("GET", "/api/v1/province/filings/" + filingId +
                "/versions/99", province, null, Map.of()).statusCode()).isEqualTo(404);
        assertThat(request("PUT", "/api/v1/enterprise/profile", enterprise, profile(), Map.of())
                .statusCode()).isEqualTo(409);
        assertThat(request("POST", "/api/v1/enterprise/filing/submit", enterprise,
                "{\"expectedVersion\":0}", Map.of()).statusCode()).isEqualTo(409);
        var queue = body(request("GET", "/api/v1/province/filings?regionCode=W2_C", province,
                null, Map.of())).path("data");
        assertThat(queue.path("total").asLong()).isEqualTo(1);
        assertThat(queue.path("items").get(0).path("filingId").asText()).isEqualTo(filingId);
        assertThat(queue.path("items").get(0).path("submittedAt").asText())
                .isEqualTo(submittedAt);
        assertThat(body(request("GET", "/api/v1/province/filings", otherProvince, null, Map.of()))
                .path("data").path("total").asLong()).isZero();
        assertThat(request("GET", "/api/v1/province/filings/" + filingId,
                otherProvince, null, Map.of()).statusCode()).isEqualTo(404);
        assertThat(request("GET", "/api/v1/province/filings/" + filingId,
                province, null, Map.of()).statusCode()).isEqualTo(200);
        assertThat(body(request("GET", "/api/v1/province/filings/" + filingId +
                "/versions/1", province, null, Map.of())).path("data").path("version").asInt())
                .isEqualTo(1);
        assertThat(request("POST", "/api/v1/province/filings/" + filingId + "/return",
                province, "{\"expectedVersion\":1,\"reason\":\" \"}", Map.of())
                .statusCode()).isEqualTo(400);
        assertThat(request("POST", "/api/v1/province/filings/" + filingId + "/return",
                province, "{\"expectedVersion\":1,\"reason\":\"请核对资料\"}", Map.of())
                .statusCode()).isEqualTo(200);
        assertThat(request("POST", "/api/v1/province/filings/" + filingId + "/approve",
                province, "{\"expectedVersion\":1}", Map.of()).statusCode()).isEqualTo(409);
        assertThat(body(request("GET", "/api/v1/enterprise/filing", enterprise, null, Map.of()))
                .path("data").path("returnReason").asText()).isEqualTo("请核对资料");

        String revised = profile().replace("演示企业甲", "演示企业甲修订");
        assertThat(request("PUT", "/api/v1/enterprise/profile", enterprise, revised, Map.of())
                .statusCode()).isEqualTo(200);
        assertThat(request("POST", "/api/v1/enterprise/filing/submit", enterprise,
                "{\"expectedVersion\":1}", Map.of()).statusCode()).isEqualTo(200);
        assertThat(body(request("GET", "/api/v1/enterprise/filing/versions/1", enterprise,
                null, Map.of())).path("data").path("profile").path("enterpriseName").asText())
                .isEqualTo("演示企业甲");
        assertThat(request("POST", "/api/v1/province/filings/" + filingId + "/approve",
                province, "{\"expectedVersion\":1}", Map.of()).statusCode()).isEqualTo(409);
        var approvedReview = request("POST", "/api/v1/province/filings/" + filingId + "/approve",
                province, "{\"expectedVersion\":2}", Map.of());
        assertThat(approvedReview.statusCode()).isEqualTo(200);
        String reviewedAt = body(approvedReview).path("data").path("reviewedAt").asText();
        var approvedStatus = body(request("GET", "/api/v1/enterprise/filing", enterprise,
                null, Map.of())).path("data");
        assertThat(approvedStatus.path("canReport").asBoolean()).isTrue();
        assertThat(approvedStatus.path("reviewedAt").asText()).isEqualTo(reviewedAt);
        assertThat(body(request("GET", "/api/v1/enterprise/filing/versions/2", enterprise,
                null, Map.of())).path("data").path("reviewedAt").asText()).isEqualTo(reviewedAt);
        assertThat(body(request("GET", "/api/v1/province/filings/" + filingId,
                province, null, Map.of())).path("data").path("reviewedAt").asText())
                .isEqualTo(reviewedAt);
        assertThat(jdbc.queryForObject("SELECT name FROM org_unit WHERE id=?",
                String.class, Long.parseLong(orgId))).isEqualTo("演示企业甲修订");

        var approved = body(request("GET", "/api/v1/province/filings?status=APPROVED" +
                "&periodCode=W2_TEST_FUTURE&regionCode=W2_C", province, null, Map.of())).path("data");
        assertThat(approved.path("total").asLong()).isEqualTo(1);
        assertThat(approved.path("items").get(0).path("enterpriseName").asText())
                .isEqualTo("演示企业甲修订");
        assertThat(approved.path("items").get(0).path("reviewedAt").asText())
                .isEqualTo(reviewedAt);
        assertThat(body(request("GET", "/api/v1/province/filings?status=APPROVED" +
                "&periodCode=W2_TEST_PAST", province, null, Map.of()))
                .path("data").path("total").asLong()).isZero();
        assertThat(body(request("GET", "/api/v1/province/filings?status=APPROVED" +
                "&regionCode=W2_C2", province, null, Map.of())).path("data").path("total").asLong())
                .isZero();
        assertThat(request("GET", "/api/v1/province/filings?periodCode=DEMO_2026_10",
                province, null, Map.of()).statusCode()).isEqualTo(400);
        assertThat(request("GET", "/api/v1/province/filings?status=APPROVED&periodCode=UNKNOWN",
                province, null, Map.of()).statusCode()).isEqualTo(400);
        var periods = body(request("GET", "/api/v1/province/filings/periods", province,
                null, Map.of())).path("data");
        var demoPeriod = periods.valueStream().filter(p -> "DEMO_2026_10".equals(
                p.path("code").asText())).findFirst().orElseThrow();
        assertThat(demoPeriod.path("startAt").asText()).isEqualTo("2026-09-30T16:00:00Z");
        assertThat(demoPeriod.path("endExclusiveAt").asText())
                .isEqualTo("2026-10-31T16:00:00Z");

        var excel = requestBytes("/api/v1/province/filings/export?regionCode=W2_C",
                province);
        assertThat(excel.statusCode()).isEqualTo(200);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(excel.body()))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("组织机构代码");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("演示企业甲修订");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).contains("演示区县");
            assertThat(sheet.getRow(1).getCell(12).getStringCellValue()).isEqualTo(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                            .withZone(ZoneId.of("Asia/Shanghai"))
                            .format(Instant.parse(reviewedAt)));
        }
        var emptyExcel = requestBytes("/api/v1/province/filings/export?regionCode=W2_C2",
                province);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(emptyExcel.body()))) {
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isZero();
        }
    }

    private void seed() {
        region("W2_P", null, "演示省二周", 1);
        region("W2_C", "W2_P", "演示市", 2);
        region("W2_D", "W2_C", "演示区县", 3);
        region("W2_C2", "W2_P", "无市局地区", 2);
        region("W2_OTHER_P", null, "外省", 1);
        region("W2_OTHER_C", "W2_OTHER_P", "外市", 2);
        jdbc.update("INSERT INTO org_unit VALUES (4100,NULL,'PROVINCE','W2_P','省级',true)");
        jdbc.update("INSERT INTO org_unit VALUES (4200,4100,'CITY','W2_C','市级',true)");
        jdbc.update("INSERT INTO org_unit VALUES (5100,NULL,'PROVINCE','W2_OTHER_P','外省级',true)");
        jdbc.update("INSERT INTO org_unit VALUES (5200,5100,'CITY','W2_OTHER_C','外市级',true)");
        user(4101, 4100, "w2_province", 3);
        user(4201, 4200, "w2_city", 2);
        user(5101, 5100, "w2_other_province", 3);
        jdbc.update("""
                INSERT INTO survey_period VALUES
                ('W2_TEST_FUTURE','测试未来期次','2020-01-01 00:00:00','2099-01-01 00:00:00',true)
                """);
        jdbc.update("""
                INSERT INTO survey_period VALUES
                ('W2_TEST_PAST','测试历史期次','2000-01-01 00:00:00','2001-01-01 00:00:00',true)
                """);
    }

    private void region(String code, String parent, String name, int level) {
        jdbc.update("INSERT INTO region VALUES (?,?,?,?)", code, parent, name, level);
    }

    private void user(long id, long org, String username, long role) {
        jdbc.update("""
                INSERT INTO user_account
                (id,org_id,username,password_hash,display_name,enabled,created_at)
                VALUES (?,?,?,?,?,true,CURRENT_TIMESTAMP)
                """, id, org, username, passwords.encode("testPass123"), username);
        jdbc.update("INSERT INTO user_role VALUES (?,?)", id, role);
    }

    private String account(String username, String region, String orgName) {
        return json.writeValueAsString(Map.of("username", username, "initialPassword", "testPass123",
                "orgName", orgName, "regionCode", region));
    }

    private String profile() {
        return """
                {"organizationCode":" AB1234567 ","enterpriseName":"演示企业甲",
                 "natureParentCode":"DEMO_NATURE_1","natureCode":"DEMO_NATURE_1_1",
                 "industryParentCode":"DEMO_INDUSTRY_1","industryCode":"DEMO_INDUSTRY_1_1",
                 "mainBusiness":"演示制造业务","contactName":"张三",
                 "addressParentCode":"DEMO_ADDR_1","addressCode":"DEMO_ADDR_1_1",
                 "postalCode":"650000","contactPhone":"0871-12345678",
                 "fax":"0871-12345678","email":"demo@example.com"}
                """;
    }

    private String login(String username, String password) throws Exception {
        var response = request("POST", "/api/v1/auth/login", null,
                json.writeValueAsString(Map.of("username", username, "password", password)), Map.of());
        assertThat(response.statusCode()).isEqualTo(200);
        return body(response).path("data").path("accessToken").asText();
    }

    private HttpResponse<String> request(String method, String path, String token,
            String payload, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (payload != null) builder.header("Content-Type", "application/json");
        headers.forEach(builder::header);
        builder.method(method, payload == null ? HttpRequest.BodyPublishers.noBody() :
                HttpRequest.BodyPublishers.ofString(payload));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<byte[]> requestBytes(String path, String token)
            throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path))
                .header("Authorization", "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
    private JsonNode body(HttpResponse<String> response) { return json.readTree(response.body()); }
}
