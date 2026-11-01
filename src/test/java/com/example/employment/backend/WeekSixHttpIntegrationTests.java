package com.example.employment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
class WeekSixHttpIntegrationTests {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void analysisUsesCurrentValuesAndNoticesRespectScope() throws Exception {
        seed();
        String province = login("w6_province");
        String city = login("w6_city");
        String enterprise = login("w6_enterprise");
        String otherProvince = login("w6_other_province");
        String base = "/api/v1/province/analysis";
        assertThat(call("GET", base+"/cities?periodCode=W6_P1",city,null).statusCode())
                .isEqualTo(403);
        JsonNode cities = data(call("GET",base+"/cities?periodCode=W6_P1",province,null));
        assertThat(cities.path("totalEnterpriseCount").asInt()).isEqualTo(2);
        assertThat(cities.path("cities").size()).isEqualTo(2);
        assertThat(cities.path("cities").get(0).path("sharePercent").asDouble()).isEqualTo(50);
        JsonNode cityFiltered = data(call("GET",base+"/cities?periodCode=W6_P1&regionCode=W6_C1",
                province,null));
        assertThat(cityFiltered.path("totalEnterpriseCount").asInt()).isEqualTo(1);
        assertThat(cityFiltered.path("cities").get(0).path("sharePercent").asDouble())
                .isEqualTo(100);
        String comparePath = base+"/compare?firstPeriodCode=W6_P1&secondPeriodCode=W6_P2";
        JsonNode compare = data(call("GET",comparePath,province,null));
        JsonNode first = compare.path("periods").get(0).path("metrics");
        assertThat(first.path("enterpriseCount").asInt()).isEqualTo(2);
        assertThat(first.path("baselineEmploymentTotal").asInt()).isEqualTo(150);
        assertThat(first.path("periodEmploymentTotal").asInt()).isEqualTo(140);
        assertThat(first.path("netChange").asInt()).isEqualTo(-10);
        assertThat(first.path("reductionTotal").asInt()).isEqualTo(20);
        assertThat(first.path("changePercent").asDouble()).isEqualTo(-6.67);
        assertThat(first.path("filingSourceCounts").path("SNAPSHOT_VERSION").asInt())
                .isEqualTo(1);
        assertThat(first.path("filingSourceCounts").path("CURRENT_FALLBACK").asInt())
                .isEqualTo(1);
        assertThat(data(call("GET",comparePath+"&natureParentCode=DEMO_NATURE_1&natureCode=DEMO_NATURE_1_1",
                province,null)).path("periods").get(0).path("metrics").path("enterpriseCount").asInt())
                .isEqualTo(2);
        assertThat(call("GET",comparePath+"&natureCode=DEMO_INDUSTRY_1_1",province,null)
                .statusCode()).isEqualTo(400);
        assertThat(call("GET",base+"/trend?periodCodes=W6_P1,W6_P3",province,null)
                .statusCode()).isEqualTo(400);
        JsonNode trend = data(call("GET",base+"/trend?periodCodes=W6_P1,W6_P2,W6_P3",
                province,null));
        assertThat(trend.path("points").size()).isEqualTo(3);
        assertThat(trend.path("points").get(2).path("hasSample").asBoolean()).isFalse();
        assertThat(trend.path("points").get(2).path("metrics").path("changePercent").isNull())
                .isTrue();
        assertThat(call("GET",comparePath,otherProvince,null).statusCode()).isEqualTo(400);

        JsonNode revised = data(call("POST","/api/v1/province/reports/96301/corrections",
                province,"{\"expectedVersion\":4,\"submissionVersion\":1,\"reason\":\"核对\",\"changes\":{\"periodEmploymentCount\":70}}"));
        assertThat(revised.path("currentVersion").asInt()).isEqualTo(5);
        first = data(call("GET",comparePath,province,null)).path("periods").get(0).path("metrics");
        assertThat(first.path("netChange").asInt()).isEqualTo(-20);
        assertThat(first.path("reductionTotal").asInt()).isEqualTo(30);
        data(call("POST","/api/v1/province/reports/96401/void",province,
                "{\"expectedVersion\":4,\"submissionVersion\":1,\"reason\":\"重复\"}"));
        first = data(call("GET",comparePath,province,null)).path("periods").get(0).path("metrics");
        assertThat(first.path("enterpriseCount").asInt()).isEqualTo(1);
        assertThat(first.path("netChange").asInt()).isEqualTo(-30);
        assertThat(first.path("changePercent").asDouble()).isEqualTo(-30);
        assertThat(data(call("GET",base+"/cities?periodCode=W6_P1",province,null))
                .path("cities").size()).isEqualTo(1);

        assertThat(call("POST","/api/v1/province/notices",city,
                "{\"title\":\"非法\",\"content\":\"正文\"}").statusCode())
                .isEqualTo(403);
        assertThat(call("POST","/api/v1/province/notices",province,
                "{\"title\":\"\",\"content\":\"正文\"}").statusCode())
                .isEqualTo(400);
        assertThat(call("POST","/api/v1/province/notices",province,
                json.writeValueAsString(Map.of("title","题".repeat(51),"content","正文")))
                .statusCode()).isEqualTo(400);
        assertThat(call("POST","/api/v1/province/notices",province,
                json.writeValueAsString(Map.of("title","标题","content","文".repeat(2001))))
                .statusCode()).isEqualTo(400);
        JsonNode notice = data(call("POST","/api/v1/province/notices",province,
                "{\"title\":\"省级通知\",\"content\":\"第一版正文\"}"),201);
        String noticeId = notice.path("noticeId").asText();
        assertThat(notice.path("currentVersion").asInt()).isEqualTo(1);
        assertThat(notice.path("publisherOrgId").asText()).isEqualTo("96000");
        assertThat(data(call("GET","/api/v1/notices",city,null))
                .path("items").get(0).path("canEdit").asBoolean()).isFalse();
        assertThat(data(call("GET","/api/v1/notices/"+noticeId,enterprise,null))
                .path("content").asText()).isEqualTo("第一版正文");
        assertThat(data(call("GET","/api/v1/notices",otherProvince,null))
                .path("total").asInt()).isZero();
        assertThat(call("GET","/api/v1/notices/"+noticeId,otherProvince,null)
                .statusCode()).isEqualTo(404);
        JsonNode updated = data(call("PUT","/api/v1/province/notices/"+noticeId,
                province,"{\"expectedVersion\":1,\"title\":\"省级通知修订\",\"content\":\"第二版正文\"}"));
        assertThat(updated.path("currentVersion").asInt()).isEqualTo(2);
        assertThat(updated.path("publishedAt").asText()).isEqualTo(notice.path("publishedAt").asText());
        assertThat(call("PUT","/api/v1/province/notices/"+noticeId,province,
                "{\"expectedVersion\":1,\"title\":\"旧版\",\"content\":\"正文\"}")
                .statusCode()).isEqualTo(409);
        String deletePath = "/api/v1/province/notices/"+noticeId+"?expectedVersion=2";
        var preflight = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+deletePath))
                .header("Origin","http://localhost:5173")
                .header("Access-Control-Request-Method","DELETE")
                .header("Access-Control-Request-Headers","authorization")
                .method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build();
        var preflightResult = client.send(preflight,HttpResponse.BodyHandlers.ofString());
        assertThat(preflightResult.statusCode()).isEqualTo(200);
        assertThat(preflightResult.headers().firstValue("Access-Control-Allow-Origin"))
                .contains("http://localhost:5173");
        assertThat(preflightResult.headers().firstValue("Access-Control-Allow-Methods"))
                .hasValueSatisfying(methods -> assertThat(methods).contains("DELETE"));
        var forbiddenDelete = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+deletePath))
                .header("Origin","http://localhost:5173")
                .header("Authorization","Bearer "+city)
                .DELETE().build();
        var denied = client.send(forbiddenDelete,HttpResponse.BodyHandlers.ofString());
        assertThat(denied.statusCode()).isEqualTo(403);
        assertThat(json.readTree(denied.body()).path("code").asText()).isEqualTo("FORBIDDEN");
        assertThat(call("DELETE","/api/v1/province/notices/"+noticeId+"?expectedVersion=1",
                province,null).statusCode()).isEqualTo(409);
        var browserDelete = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+deletePath))
                .header("Origin","http://localhost:5173")
                .header("Authorization","Bearer "+province)
                .DELETE().build();
        assertThat(data(client.send(browserDelete,HttpResponse.BodyHandlers.ofString()))
                .path("deleted").asBoolean()).isTrue();
        assertThat(call("GET","/api/v1/notices/"+noticeId,enterprise,null)
                .statusCode()).isEqualTo(404);
        assertThat(data(call("GET","/api/v1/notices",province,null))
                .path("total").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM province_notice_event WHERE notice_id=?",
                Integer.class,Long.parseLong(noticeId))).isEqualTo(3);
        JsonNode boundary = data(call("POST","/api/v1/province/notices",province,
                json.writeValueAsString(Map.of("title","题".repeat(50),
                        "content","文".repeat(2000)))),201);
        assertThat(boundary.path("title").asText().length()).isEqualTo(50);
        assertThat(boundary.path("content").asText().length()).isEqualTo(2000);
        data(call("DELETE","/api/v1/province/notices/"+boundary.path("noticeId").asText()
                +"?expectedVersion=1",province,null));
    }

    private void seed() {
        jdbc.update("INSERT INTO region VALUES ('W6_P',NULL,'六周省',1)");
        jdbc.update("INSERT INTO region VALUES ('W6_C1','W6_P','A市',2)");
        jdbc.update("INSERT INTO region VALUES ('W6_C2','W6_P','B市',2)");
        jdbc.update("INSERT INTO region VALUES ('W6_OP',NULL,'外省',1)");
        jdbc.update("INSERT INTO org_unit VALUES (96000,NULL,'PROVINCE','W6_P','六周省',true)");
        jdbc.update("INSERT INTO org_unit VALUES (96100,96000,'CITY','W6_C1','A市',true)");
        jdbc.update("INSERT INTO org_unit VALUES (96200,96000,'CITY','W6_C2','B市',true)");
        jdbc.update("INSERT INTO org_unit VALUES (96300,96100,'ENTERPRISE','W6_C1','A企业',true)");
        jdbc.update("INSERT INTO org_unit VALUES (96400,96200,'ENTERPRISE','W6_C2','B企业',true)");
        jdbc.update("INSERT INTO org_unit VALUES (97000,NULL,'PROVINCE','W6_OP','外省',true)");
        user(96010,96000,"w6_province",3); user(96110,96100,"w6_city",2);
        user(96310,96300,"w6_enterprise",1); user(97010,97000,"w6_other_province",3);
        jdbc.update("""
                INSERT INTO enterprise_filing (id,org_id,status,current_version,
                    nature_parent_code,nature_code,industry_parent_code,industry_code,updated_at)
                VALUES (96800,96300,'APPROVED',1,'DEMO_NATURE_1','DEMO_NATURE_1_1',
                    'DEMO_INDUSTRY_1','DEMO_INDUSTRY_1_1',CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO enterprise_filing (id,org_id,status,current_version,
                    nature_parent_code,nature_code,industry_parent_code,industry_code,updated_at)
                VALUES (96801,96400,'APPROVED',1,'DEMO_NATURE_1','DEMO_NATURE_1_1',
                    'DEMO_INDUSTRY_1','DEMO_INDUSTRY_1_1',CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO filing_version (filing_id,version,result,organization_code,
                    enterprise_name,nature_parent_code,nature_code,industry_parent_code,
                    industry_code,main_business,contact_name,address_parent_code,address_code,
                    postal_code,contact_phone,fax,submitted_at,submitted_by)
                VALUES (96800,1,'APPROVED','W6A00001','A企业','DEMO_NATURE_1',
                    'DEMO_NATURE_1_1','DEMO_INDUSTRY_1','DEMO_INDUSTRY_1_1',
                    '测试业务','联系人','DEMO_ADDR_1','DEMO_ADDR_1_1','100000',
                    '13800000000','01000000000',CURRENT_TIMESTAMP,96310)
                """);
        period("W6_P1","2026-01-01 00:00:00");
        period("W6_P2","2026-02-01 00:00:00");
        period("W6_P3","2026-03-01 00:00:00");
        report(96301,96300,"W6_P1",100,80,96310,true);
        report(96401,96400,"W6_P1",50,60,96310,false);
        report(96302,96300,"W6_P2",100,90,96310,true);
        report(96402,96400,"W6_P2",50,65,96310,false);
    }
    private void user(long id,long org,String name,int role) {
        jdbc.update("""
                INSERT INTO user_account (id,org_id,username,password_hash,display_name,enabled,created_at)
                VALUES (?,?,?,?,?,true,CURRENT_TIMESTAMP)
                """,id,org,name,passwords.encode("testPass123"),name);
        jdbc.update("INSERT INTO user_role VALUES (?,?)",id,role);
    }
    private void period(String code,String start) {
        jdbc.update("""
                INSERT INTO survey_period (code,label,start_at,end_exclusive_at,demo,
                    province_org_id,open_at,close_at,current_version)
                VALUES (?,?,?, '2027-01-01 00:00:00',false,96000,
                    '2020-01-01 00:00:00','2027-01-01 00:00:00',1)
                """,code,code,start);
    }
    private void report(long id,long org,String period,int baseline,int current,
            long submitter,boolean frozen) {
        boolean reduced=current<baseline;
        jdbc.update("""
                INSERT INTO enterprise_report (id,org_id,period_code,status,current_version,
                    submission_version,baseline_employment_count,period_employment_count,
                    other_reason,reduction_type_code,primary_reason_code,primary_reason_detail,
                    updated_at,submitted_at)
                VALUES (?,?,?,'APPROVED_PROVINCE',4,1,?,?,'无',?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """,id,org,period,baseline,current,reduced?"TYPE_OTHER":null,
                reduced?"REASON_OTHER":null,reduced?"测试原因":null);
        jdbc.update("""
                INSERT INTO report_snapshot (report_id,submission_version,
                    baseline_employment_count,period_employment_count,other_reason,
                    reduction_type_code,primary_reason_code,primary_reason_detail,
                    submitted_at,submitted_by,result,filing_id,filing_version)
                VALUES (?,1,?,?,'无',?,?,?,CURRENT_TIMESTAMP,?,'SUBMITTED_CITY',?,?)
                """,id,baseline,current,reduced?"TYPE_OTHER":null,
                reduced?"REASON_OTHER":null,reduced?"测试原因":null,submitter,
                frozen?96800L:null,frozen?1:null);
    }
    private String login(String username) throws Exception {
        return data(call("POST","/api/v1/auth/login",null,
                json.writeValueAsString(Map.of("username",username,"password","testPass123"))))
                .path("accessToken").asText();
    }
    private HttpResponse<String> call(String method,String path,String token,String body) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));
        if(token!=null) request.header("Authorization","Bearer "+token);
        if(body!=null) request.header("Content-Type","application/json");
        request.method(method,body==null?HttpRequest.BodyPublishers.noBody():
                HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode data(HttpResponse<String> response) throws Exception { return data(response,200); }
    private JsonNode data(HttpResponse<String> response,int status) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        return json.readTree(response.body()).path("data");
    }
}
