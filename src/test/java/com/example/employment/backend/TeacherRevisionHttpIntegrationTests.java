package com.example.employment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class TeacherRevisionHttpIntegrationTests {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;
    private final HttpClient http=HttpClient.newHttpClient();

    @Test
    void rollingBatchCityNoticeProgressAndAnalysis() throws Exception {
        seed();
        String enterprise=login("rev_enterprise"),city=login("rev_city"),
                province=login("rev_province"),otherCity=login("rev_other_city");
        String reports="/api/v1/enterprise/reports/";
        JsonNode first=data(call("PUT",reports+"REV_P1",enterprise,form(0,null,100,false)));
        assertThat(first.path("computedBaselineEmploymentCount").asInt()).isEqualTo(100);
        assertThat(first.path("baselineSourcePeriodCode").asText()).isEqualTo("REV_P1");
        JsonNode submitted=data(call("POST",reports+"REV_P1/submit",enterprise,
                "{\"expectedVersion\":1}"));
        assertThat(submitted.path("baselineSourcePeriodCode").asText()).isEqualTo("REV_P1");
        assertThat(submitted.path("baselineEmploymentCount").asInt()).isEqualTo(100);
        assertThat(data(call("PUT",reports+"REV_P2",enterprise,form(0,null,90,true)))
                .path("computedBaselineEmploymentCount").asInt()).isEqualTo(100);
        data(call("PUT",reports+"REV_P3",enterprise,form(0,null,80,true)));
        JsonNode batch=data(call("POST",reports+"batch-submit",enterprise,
                "{\"items\":[{\"periodCode\":\"REV_P3\",\"expectedVersion\":1},"
                +"{\"periodCode\":\"REV_P2\",\"expectedVersion\":0}]}"));
        assertThat(batch.path("successCount").asInt()).isEqualTo(1);
        assertThat(batch.path("failureCount").asInt()).isEqualTo(1);
        assertThat(batch.path("items").get(0).path("periodCode").asText()).isEqualTo("REV_P2");
        assertThat(batch.path("items").get(0).path("reasonCode").asText())
                .isEqualTo("VERSION_CONFLICT");
        assertThat(batch.path("items").get(1).path("baselineSourcePeriodCode").asText())
                .isEqualTo("REV_P1");
        assertThat(call("POST",reports+"batch-submit",enterprise,
                "{\"items\":[{\"periodCode\":\"REV_P2\",\"expectedVersion\":1},"
                +"{\"periodCode\":\"REV_P2\",\"expectedVersion\":1}]}" ).statusCode())
                .isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT submission_version FROM enterprise_report "
                +"WHERE org_id=99300 AND period_code='REV_P2'",Integer.class)).isZero();
        assertThat(data(call("GET","/api/v1/enterprise/work-overview?periodCode=REV_P2",
                enterprise,null)).path("reportStatus").asText()).isEqualTo("DRAFT");

        JsonNode notice=data(call("POST","/api/v1/city/notices",city,
                "{\"title\":\"本市提醒\",\"content\":\"请填报\"}"),201);
        String noticeId=notice.path("noticeId").asText();
        assertThat(notice.path("publisherLevel").asText()).isEqualTo("CITY");
        assertThat(data(call("GET","/api/v1/notices",enterprise,null)).path("total").asInt())
                .isEqualTo(1);
        assertThat(data(call("GET","/api/v1/notices",province,null)).path("total").asInt())
                .isZero();
        assertThat(data(call("GET","/api/v1/notices",otherCity,null)).path("total").asInt())
                .isZero();
        assertThat(call("PUT","/api/v1/city/notices/"+noticeId,otherCity,
                "{\"expectedVersion\":1,\"title\":\"越权\",\"content\":\"内容\"}")
                .statusCode()).isEqualTo(404);
        assertThat(data(call("DELETE","/api/v1/city/notices/"+noticeId
                +"?expectedVersion=1",city,null)).path("deleted").asBoolean()).isTrue();

        JsonNode progress=data(call("GET","/api/v1/province/enterprise-progress"
                +"?periodCode=REV_P2",province,null));
        assertThat(progress.path("summary").path("totalEnterprises").asInt()).isEqualTo(2);
        assertThat(progress.path("summary").path("filingCounts").path("NO_FILING").asInt())
                .isEqualTo(1);
        assertThat(data(call("GET","/api/v1/province/enterprise-progress"
                +"?periodCode=REV_P2&reportStatus=NOT_SUBMITTED",province,null))
                .path("total").asInt()).isEqualTo(1);
        assertThat(data(call("GET","/api/v1/city/work-overview?periodCode=REV_P2",
                city,null)).path("reportCounts").path("notSubmitted").asInt()).isEqualTo(2);
        assertThat(data(call("GET","/api/v1/province/work-overview?periodCode=REV_P2",
                province,null)).path("cityProgress").path("items").size()).isEqualTo(2);

        jdbc.update("UPDATE enterprise_report SET status='APPROVED_PROVINCE' "
                +"WHERE org_id=99300 AND period_code IN ('REV_P1','REV_P3')");
        String base="/api/v1/province/analysis";
        JsonNode compared=data(call("GET",base+"/compare?firstPeriodCode=REV_P1"
                +"&secondPeriodCode=REV_P3&enterpriseOrgId=99300",province,null));
        assertThat(compared.path("periods").get(1).path("metrics").path("netChange").asInt())
                .isEqualTo(-20);
        JsonNode breakdown=data(call("GET",base+"/breakdown?periodCode=REV_P3"
                +"&groupBy=CHANGE_DIRECTION",province,null));
        assertThat(breakdown.path("items").get(0).path("reductionTotal").asInt())
                .isEqualTo(20);
        JsonNode detail=data(call("GET",base+"/enterprises?periodCode=REV_P3"
                +"&changeDirection=DECREASE",province,null));
        assertThat(detail.path("total").asInt()).isEqualTo(1);
        assertThat(detail.path("items").get(0).path("baselineSourcePeriodCode").asText())
                .isEqualTo("REV_P1");
        assertThat(data(call("GET",base+"/trend?periodCodes=REV_P1,REV_P2,REV_P3",
                province,null)).path("points").get(1).path("metrics").isNull()).isTrue();
        String firstReportId=jdbc.queryForObject("SELECT id FROM enterprise_report "
                +"WHERE org_id=99300 AND period_code='REV_P1'",Long.class).toString();
        data(call("POST","/api/v1/province/reports/"+firstReportId+"/void",province,
                "{\"expectedVersion\":2,\"submissionVersion\":1,\"reason\":\"重复数据\"}"));
        JsonNode frozen=data(call("GET",reports+"REV_P3",enterprise,null));
        assertThat(frozen.path("baselineSourcePeriodCode").asText()).isEqualTo("REV_P1");
        assertThat(frozen.path("computedBaselineEmploymentCount").asInt()).isEqualTo(100);
    }

    private void seed() {
        jdbc.update("INSERT INTO region VALUES ('REV_P',NULL,'修订省',1)");
        jdbc.update("INSERT INTO region VALUES ('REV_C1','REV_P','修订市一',2)");
        jdbc.update("INSERT INTO region VALUES ('REV_C2','REV_P','修订市二',2)");
        jdbc.update("INSERT INTO org_unit VALUES (99000,NULL,'PROVINCE','REV_P','修订省',true)");
        jdbc.update("INSERT INTO org_unit VALUES (99100,99000,'CITY','REV_C1','修订市一',true)");
        jdbc.update("INSERT INTO org_unit VALUES (99200,99000,'CITY','REV_C2','修订市二',true)");
        jdbc.update("INSERT INTO org_unit VALUES (99300,99100,'ENTERPRISE','REV_C1','修订企业',true)");
        jdbc.update("INSERT INTO org_unit VALUES (99400,99100,'ENTERPRISE','REV_C1','未备案企业',true)");
        user(99010,99000,"rev_province",3);user(99110,99100,"rev_city",2);
        user(99210,99200,"rev_other_city",2);user(99310,99300,"rev_enterprise",1);
        jdbc.update("INSERT INTO enterprise_filing (id,org_id,status,current_version,updated_at) "
                +"VALUES (99800,99300,'APPROVED',1,CURRENT_TIMESTAMP)");
        jdbc.update("""
                INSERT INTO filing_version (filing_id,version,result,organization_code,
                    enterprise_name,nature_parent_code,nature_code,industry_parent_code,
                    industry_code,main_business,contact_name,address_parent_code,address_code,
                    postal_code,contact_phone,fax,submitted_at,submitted_by)
                VALUES (99800,1,'APPROVED','REV000001','修订企业','DEMO_NATURE_1',
                    'DEMO_NATURE_1_1','DEMO_INDUSTRY_1','DEMO_INDUSTRY_1_1',
                    '测试业务','联系人','DEMO_ADDR_1','DEMO_ADDR_1_1','100000',
                    '13800000000','01000000000',CURRENT_TIMESTAMP,99310)
                """);
        period("REV_P1","2026-01-01 00:00:00");
        period("REV_P2","2026-02-01 00:00:00");
        period("REV_P3","2026-03-01 00:00:00");
    }
    private void period(String code,String start) {
        jdbc.update("""
                INSERT INTO survey_period (code,label,start_at,end_exclusive_at,demo,
                    province_org_id,open_at,close_at,current_version)
                VALUES (?,?,?, '2027-01-01 00:00:00',false,99000,
                    '2020-01-01 00:00:00','2027-01-01 00:00:00',1)
                """,code,code,start);
    }
    private void user(long id,long org,String name,int role) {
        jdbc.update("INSERT INTO user_account (id,org_id,username,password_hash,display_name,enabled,created_at) "
                +"VALUES (?,?,?,?,?,true,CURRENT_TIMESTAMP)",id,org,name,
                passwords.encode("testPass123"),name);
        jdbc.update("INSERT INTO user_role VALUES (?,?)",id,role);
    }
    private String form(int version,Integer baseline,int current,boolean reduced) throws Exception {
        Map<String,Object> values=new LinkedHashMap<>();
        values.put("expectedVersion",version);values.put("baselineEmploymentCount",baseline);
        values.put("periodEmploymentCount",current);values.put("otherReason","无");
        values.put("reductionTypeCode",reduced?"TYPE_OTHER":null);
        values.put("primaryReasonCode",reduced?"REASON_VOLUNTARY_LEAVE":null);
        values.put("primaryReasonDetail",reduced?"离职":null);
        values.put("secondaryReasonCode",null);values.put("secondaryReasonDetail",null);
        values.put("tertiaryReasonCode",null);values.put("tertiaryReasonDetail",null);
        return json.writeValueAsString(values);
    }
    private String login(String name) throws Exception {
        return data(call("POST","/api/v1/auth/login",null,
                json.writeValueAsString(Map.of("username",name,"password","testPass123"))))
                .path("accessToken").asText();
    }
    private HttpResponse<String> call(String method,String path,String token,String body) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));
        if (token!=null) request.header("Authorization","Bearer "+token);
        if (body!=null) request.header("Content-Type","application/json");
        request.method(method,body==null?HttpRequest.BodyPublishers.noBody():
                HttpRequest.BodyPublishers.ofString(body));
        return http.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode data(HttpResponse<String> response) throws Exception { return data(response,200); }
    private JsonNode data(HttpResponse<String> response,int status) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        return json.readTree(response.body()).path("data");
    }
}
