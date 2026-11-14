package com.example.employment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.employment.backend.week1.security.TokenService;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class YearHttpIntegrationTests {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired TokenService tokens;
    @Autowired JsonMapper json;
    private final HttpClient http=HttpClient.newHttpClient();

    @Test
    void createsTwelveMonthsAtomicallyAndReusesIdenticalYear() throws Exception {
        jdbc.update("INSERT INTO user_account (id,org_id,username,password_hash,display_name,enabled,created_at) VALUES (55001,2100,'year_province','x','省',true,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO user_role VALUES (55001,3),(55001,4)");
        String token=tokens.issue(55001),base="/api/v1/province/periods/year";
        JsonNode preview=data(call("GET",base+"/preview?year=2028",token,null),200);
        assertThat(preview.path("months").size()).isEqualTo(12);
        assertThat(preview.path("months").get(1).path("endExclusiveAt").asText())
                .isEqualTo("2028-02-29T16:00:00Z");
        assertThat(preview.path("canCreate").asBoolean()).isTrue();
        JsonNode result=data(call("POST",base,token,"{\"year\":2028}"),200);
        assertThat(result.path("created").size()).isEqualTo(12);
        assertThat(result.path("existing").size()).isZero();
        assertThat(result.path("created").get(11).path("closeAt").asText())
                .isEqualTo("2029-01-07T16:00:00Z");
        result=data(call("POST",base,token,"{\"year\":2028}"),200);
        assertThat(result.path("created").size()).isZero();
        assertThat(result.path("existing").size()).isEqualTo(12);
        jdbc.update("""
                INSERT INTO survey_period(code,label,start_at,end_exclusive_at,demo,province_org_id,
                    open_at,close_at,current_version) VALUES
                ('P202907','冲突','2029-06-30 16:00:00','2029-07-31 16:00:00',false,2100,
                    '2029-07-31 16:00:00','2029-08-08 16:00:00',1)
                """);
        assertThat(data(call("POST",base,token,"{\"year\":2029}"),409)
                .path("reasonCode").asText()).isEqualTo("YEAR_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_period WHERE code LIKE 'P2029%'",
                Integer.class)).isEqualTo(1);
        assertThat(call("POST",base,token,"{\"year\":2028,\"provinceOrgId\":2100}")
                .statusCode()).isEqualTo(400);
    }
    private HttpResponse<String> call(String method,String path,String token,String body) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
                .timeout(Duration.ofSeconds(10)).header("Authorization","Bearer "+token);
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
