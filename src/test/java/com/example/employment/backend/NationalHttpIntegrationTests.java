package com.example.employment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.employment.backend.midterm.national.repository.QueueRepository;
import com.example.employment.backend.midterm.national.service.QueueReceiver;
import com.example.employment.backend.midterm.national.service.QueueSender;
import com.example.employment.backend.midterm.national.service.XmlReport;
import com.example.employment.backend.week1.security.TokenService;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class NationalHttpIntegrationTests {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired TokenService tokens;
    @Autowired JsonMapper json;
    @Autowired QueueRepository queue;
    @Autowired XmlReport xml;
    @Autowired TransactionTemplate tx;
    @MockitoBean QueueSender sender;
    private final HttpClient http=HttpClient.newHttpClient();

    @Test
    void proxyFreezeAndReceiveChunks() throws Exception {
        seed();String province=tokens.issue(54001),city=tokens.issue(54002);
        String base="/api/v1/province/national";
        assertThat(call("GET",base+"/periods/NAT_TEST_1/readiness",city,null).statusCode()).isEqualTo(403);
        JsonNode blocked=data(call("GET",base+"/periods/NAT_TEST_1/readiness",province,null),200);
        assertThat(blocked.path("counts").path("eligibleCount").asInt()).isEqualTo(4);
        assertThat(blocked.path("counts").path("pendingCityCount").asInt()).isEqualTo(1);
        assertThat(data(call("POST",base+"/periods/NAT_TEST_1/proxy-fill",province,"{}"),409)
                .path("reasonCode").asText()).isEqualTo("NO_REAL_SOURCE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM national_proxy_item",Integer.class)).isZero();

        previous(54203,54103,30);previous(54205,54104,20);
        JsonNode filled=data(call("POST",base+"/periods/NAT_TEST_1/proxy-fill",province,"{}"),200);
        assertThat(filled.path("generatedCount").asInt()).isEqualTo(1);
        assertThat(data(call("POST",base+"/periods/NAT_TEST_1/proxy-fill",province,
                "{\"expectedVersion\":0}"),409).path("reasonCode").asText()).isEqualTo("VERSION_CONFLICT");
        assertThat(data(call("POST",base+"/batches",province,
                "{\"periodCode\":\"NAT_TEST_1\",\"schemaVersion\":\"demo-2\"}"),409)
                .path("reasonCode").asText()).isEqualTo("NOT_READY");

        jdbc.update("UPDATE enterprise_report SET status='DRAFT',submission_version=0 WHERE id=54204");
        filled=data(call("POST",base+"/periods/NAT_TEST_1/proxy-fill",province,
                "{\"expectedVersion\":1}"),200);
        assertThat(filled.path("generatedCount").asInt()).isEqualTo(2);
        JsonNode ready=data(call("GET",base+"/periods/NAT_TEST_1/readiness",province,null),200);
        assertThat(ready.path("canSubmit").asBoolean()).isTrue();
        assertThat(ready.path("counts").path("missingCount").asInt()).isZero();
        assertThat(call("POST",base+"/batches",province,
                "{\"periodCode\":\"NAT_TEST_1\",\"acknowledgeMissing\":true}").statusCode()).isEqualTo(400);
        JsonNode queued=data(call("POST",base+"/batches",province,
                "{\"periodCode\":\"NAT_TEST_1\",\"schemaVersion\":\"demo-2\"}"),201);
        String id=queued.path("batchId").asText();
        assertThat(queued.path("proxyCount").asInt()).isEqualTo(2);
        JsonNode detail=data(call("GET",base+"/batches/"+id,province,null),200);
        String payload=detail.path("payloadXml").asText();
        assertThat(payload).contains("<ProvinceReport schemaVersion=\"demo-2\">")
                .contains("reportMode=\"PROXY\"").doesNotContain("<Cities>");
        assertThat(detail.path("payload").isNull()).isTrue();xml.validate(payload);
        assertThat(detail.path("payloadSha256").asText()).isEqualTo(XmlReport.hash(payload));
        assertThat(data(call("DELETE",base+"/periods/NAT_TEST_1/proxy-fill",province,null),409)
                .path("reasonCode").asText()).isEqualTo("BATCH_FROZEN");

        QueueReceiver receiver=new QueueReceiver(queue,xml,tx);
        tx.executeWithoutResult(status->queue.start(id,1,java.time.Instant.now()));
        byte[] full=payload.getBytes(StandardCharsets.UTF_8);int count=(full.length+1023)/1024;
        assertThat(count).isGreaterThan(1);
        receiver.receive(chunk(id,full,count,count-1));
        assertThat(data(call("GET",base+"/batches/"+id,province,null),200)
                .path("receipt").isNull()).isTrue();
        for(int i=0;i<count-1;i++) receiver.receive(chunk(id,full,count,i));
        receiver.receive(chunk(id,full,count,0));
        detail=data(call("GET",base+"/batches/"+id,province,null),200);
        assertThat(detail.path("status").asText()).isEqualTo("MOCK_ACCEPTED");
        assertThat(detail.path("chunkProgress").path("receiverStoredCount").asInt()).isEqualTo(count);
        assertThat(detail.path("receipt").path("acceptedEnterpriseCount").asInt()).isEqualTo(4);
        assertThat(data(call("POST",base+"/batches/"+id+"/retry",province,null),409)
                .path("reasonCode").asText()).isEqualTo("RETRY_NOT_ALLOWED");
        assertThat(data(call("POST","/api/v1/province/reports/54201/void",province,
                "{\"expectedVersion\":2,\"submissionVersion\":1,\"reason\":\"测试\"}"),409)
                .path("reasonCode").asText()).isEqualTo("LOCKED_BY_NATIONAL_BATCH");
        assertThat(data(call("POST","/api/v1/province/reports/54203/void",province,
                "{\"expectedVersion\":1,\"submissionVersion\":1,\"reason\":\"测试\"}"),409)
                .path("reasonCode").asText()).isEqualTo("LOCKED_BY_NATIONAL_BATCH");
        assertThat(data(call("GET",base+"/batches",province,null),200).path("items").get(0)
                .path("payloadFormat").asText()).isEqualTo("XML");
        String legacy="YN-NAT_PREV-0001";
        String oldPayload="""
                {"schemaVersion":"demo-1","batchId":"YN-NAT_PREV-0001","mode":"MOCK",
                "provinceCode":"53","periodCode":"NAT_PREV",
                "periodStartAt":"2026-08-31T16:00:00Z",
                "periodEndExclusiveAt":"2026-09-30T16:00:00Z",
                "generatedAt":"2026-11-01T00:00:00Z",
                "summary":{"enterpriseCount":0,"baselineEmploymentTotal":0,
                "periodEmploymentTotal":0,"netChange":0},"cities":[],"enterprises":[]}
                """;
        jdbc.update("""
                INSERT INTO national_batch(batch_id,province_org_id,period_code,mode,status,
                    payload_json,payload_sha256,enterprise_count,baseline_total,period_total,
                    net_change,created_at) VALUES(?,2100,'NAT_PREV','MOCK','QUEUED',?,?,0,0,0,0,CURRENT_TIMESTAMP)
                """,legacy,oldPayload,XmlReport.hash(oldPayload));
        JsonNode old=data(call("GET",base+"/batches/"+legacy,province,null),200);
        assertThat(old.path("payloadFormat").asText()).isEqualTo("JSON");
        assertThat(old.path("payload").path("schemaVersion").asText()).isEqualTo("demo-1");
        assertThat(old.path("payloadXml").isNull()).isTrue();
        assertThat(data(call("POST",base+"/batches/"+legacy+"/retry",province,null),409)
                .path("reasonCode").asText()).isEqualTo("V1_READ_ONLY");
    }
    private Message chunk(String id,byte[] full,int count,int index) {
        byte[] bytes=Arrays.copyOfRange(full,index*1024,Math.min((index+1)*1024,full.length));
        MessageProperties p=new MessageProperties();p.setHeader("batchId",id);
        p.setHeader("chunkIndex",index);p.setHeader("chunkCount",count);
        p.setHeader("payloadSha256",XmlReport.hash(full));p.setHeader("chunkSha256",XmlReport.hash(bytes));
        return new Message(bytes,p);
    }
    private void seed() {
        jdbc.update("INSERT INTO user_account VALUES (54001,2100,'nat_province','x','省',true,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO user_account VALUES (54002,2101,'nat_city','x','市',true,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO user_role VALUES (54001,3)");jdbc.update("INSERT INTO user_role VALUES (54002,2)");
        jdbc.update("""
                INSERT INTO survey_period(code,label,start_at,end_exclusive_at,demo,province_org_id,
                    open_at,close_at,current_version) VALUES
                ('NAT_PREV','此前调查期','2026-08-31 16:00:00','2026-09-30 16:00:00',false,2100,
                    '2026-09-01 00:00:00','2026-09-08 00:00:00',1),
                ('NAT_TEST_1','国家接口测试','2026-09-30 16:00:00','2026-10-31 16:00:00',false,2100,
                    '2026-10-01 00:00:00','2026-11-01 00:00:00',1)
                """);
        org(54101,2101,"530102");org(54102,2102,"530302");
        org(54103,2101,"530103");org(54104,2102,"530303");
        filing(54101,54301,"NAT00001");filing(54102,54302,"NAT00002");
        filing(54103,54303,"NAT00003");filing(54104,54304,"NAT00004");
        report(54201,54101,"APPROVED_PROVINCE",100,90);
        report(54202,54102,"APPROVED_PROVINCE",50,55);
        report(54204,54104,"SUBMITTED_CITY",20,20);
    }
    private void org(long id,long city,String region) {
        jdbc.update("INSERT INTO org_unit VALUES (?,?,'ENTERPRISE',?,?,true)",id,city,region,"国家接口测试企业"+id);
    }
    private void filing(long org,long id,String code) {
        jdbc.update("""
                INSERT INTO enterprise_filing(id,org_id,status,current_version,organization_code,
                    enterprise_name,nature_code,industry_code,updated_at)
                VALUES(?,?,'APPROVED',1,?,?,'DEMO_NATURE_1_1','DEMO_INDUSTRY_1_1','2026-10-01 00:00:00')
                """,id,org,code,"国家接口测试企业"+org);
    }
    private void report(long id,long org,String status,int baseline,int current) {
        jdbc.update("""
                INSERT INTO enterprise_report(id,org_id,period_code,status,current_version,
                    submission_version,baseline_employment_count,period_employment_count,
                    other_reason,reduction_type_code,primary_reason_code,primary_reason_detail,
                    updated_at,baseline_source_period_code)
                VALUES(?,?,'NAT_TEST_1',?,2,1,?,?,'无',?,?,?,CURRENT_TIMESTAMP,'NAT_PREV')
                """,id,org,status,baseline,current,
                current<baseline?"TYPE_ECONOMIC_LAYOFF":null,
                current<baseline?"REASON_INSUFFICIENT_ORDERS":null,
                current<baseline?"订单不足":null);
    }
    private void previous(long id,long org,int count) {
        jdbc.update("""
                INSERT INTO enterprise_report(id,org_id,period_code,status,current_version,
                    submission_version,baseline_employment_count,period_employment_count,
                    other_reason,updated_at,baseline_source_period_code)
                VALUES (?,?,'NAT_PREV','APPROVED_PROVINCE',1,1,?,?, '无',CURRENT_TIMESTAMP,'NAT_PREV')
                """,id,org,count,count);
    }
    private HttpResponse<String> call(String method,String path,String token,String body) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
                .timeout(Duration.ofSeconds(10));
        if(token!=null) request.header("Authorization","Bearer "+token);
        if(body!=null) request.header("Content-Type","application/json");
        request.method(method,body==null?HttpRequest.BodyPublishers.noBody():
                HttpRequest.BodyPublishers.ofString(body));
        return http.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode data(HttpResponse<String> response,int status) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        return json.readTree(response.body()).path("data");
    }
}
