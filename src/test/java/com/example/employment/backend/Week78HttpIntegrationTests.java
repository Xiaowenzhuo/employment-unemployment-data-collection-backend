package com.example.employment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Week78HttpIntegrationTests {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;
    @Autowired RequestMappingHandlerMapping mappings;
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void everyBusinessRouteHasPermissionMapping() {
        Set<String> expected = new HashSet<>();
        for (var info : mappings.getHandlerMethods().keySet()) {
            if (info.getPathPatternsCondition() == null) continue;
            for (var pattern : info.getPathPatternsCondition().getPatterns()) {
                String path = pattern.getPatternString();
                if (!path.startsWith("/api/v1/")) continue;
                for (var method : info.getMethodsCondition().getMethods()) {
                    String route = method.name() + " " + path.substring("/api/v1".length());
                    if (route.contains("{level:city|province}")) {
                        expected.add(route.replace("{level:city|province}", "city"));
                        expected.add(route.replace("{level:city|province}", "province"));
                    } else {
                        expected.add(route);
                    }
                }
            }
        }
        expected.removeAll(Set.of("GET /health","POST /auth/login","GET /auth/me",
                "GET /meta/regions","GET /enterprise/filing/options",
                "GET /enterprise/reports/options"));
        Set<String> mapped = new HashSet<>(jdbc.query("""
                SELECT http_method,path_pattern FROM permission_route
                """, (rs,row) -> rs.getString(1)+" "+rs.getString(2)));
        assertThat(mapped).isEqualTo(expected);
    }

    @Test
    void usersRolesPermissionsAndMonitorWorkThroughHttp() throws Exception {
        seed();
        String admin = login("w78_admin", "Password123!");
        JsonNode me = call("GET", "/auth/me", admin, null).path("data").path("user");
        assertThat(me.path("roleCode").asText()).isEqualTo("PROVINCE");
        assertThat(me.path("functionalRoles").get(0).path("code").asText())
                .isEqualTo("PROVINCE_DEFAULT");
        assertThat(me.path("permissions").toString()).contains("PROVINCE.MONITOR.READ");

        var monitor = send("GET", "/province/admin/monitor", admin, null);
        assertThat(monitor.statusCode()).isEqualTo(200);
        assertThat(json.readTree(monitor.body()).path("data").path("mysql")
                .path("status").asText()).isEqualTo("UP");
        assertThat(json.readTree(monitor.body()).path("data").path("disk")
                .path("status").asText()).isEqualTo("UP");

        var roleResponse = send("POST", "/province/admin/roles", admin,
                Map.of("name","省级只看用户","level","PROVINCE",
                        "permissionCodes",new String[]{"PROVINCE.USER.READ"}));
        assertThat(roleResponse.statusCode()).isEqualTo(201);
        String roleId = json.readTree(roleResponse.body()).path("data").path("id").asText();
        var created = send("POST", "/province/admin/users", admin,
                Map.of("orgId","199000","username","w78_reader",
                        "displayName","只读人员","initialPassword","Password123!",
                        "functionalRoleIds",new String[]{roleId}));
        assertThat(created.statusCode()).isEqualTo(201);
        String userId = json.readTree(created.body()).path("data").path("id").asText();
        String reader = login("w78_reader", "Password123!");
        assertThat(send("GET","/province/admin/users",reader,null).statusCode())
                .isEqualTo(200);
        assertThat(send("GET","/province/admin/monitor",reader,null).statusCode())
                .isEqualTo(403);
        assertThat(send("GET","/province/work-overview",reader,null).statusCode())
                .isEqualTo(403);
        var widened=send("PUT","/province/admin/roles/"+roleId,admin,
                Map.of("name","省级只看用户","permissionCodes",
                        new String[]{"PROVINCE.USER.READ","PROVINCE.MONITOR.READ"},
                        "expectedVersion",1));
        assertThat(widened.statusCode()).isEqualTo(200);
        assertThat(send("GET","/province/admin/monitor",reader,null).statusCode())
                .isEqualTo(200);
        var narrowed=send("PUT","/province/admin/roles/"+roleId,admin,
                Map.of("name","省级只看用户","permissionCodes",
                        new String[]{"PROVINCE.USER.READ"},"expectedVersion",2));
        assertThat(narrowed.statusCode()).isEqualTo(200);
        assertThat(send("GET","/province/admin/monitor",reader,null).statusCode())
                .isEqualTo(403);
        assertThat(send("GET","/province/admin/users",login("w78_city","Password123!"),null)
                .statusCode()).isEqualTo(403);

        var stale = send("PUT","/province/admin/users/"+userId,admin,
                Map.of("displayName","改名","functionalRoleIds",new String[]{roleId},
                        "expectedVersion",99));
        assertThat(stale.statusCode()).isEqualTo(409);
        assertThat(json.readTree(stale.body()).path("data").path("reasonCode").asText())
                .isEqualTo("VERSION_CONFLICT");
        var updated = send("PUT","/province/admin/users/"+userId,admin,
                Map.of("displayName","改名","functionalRoleIds",new String[]{roleId},
                        "expectedVersion",1));
        assertThat(updated.statusCode()).isEqualTo(200);
        var reset = send("POST","/province/admin/users/"+userId+"/reset-password",admin,
                Map.of("newPassword","Newpass123!","expectedVersion",2));
        assertThat(reset.statusCode()).isEqualTo(200);
        assertThat(send("GET","/auth/me",reader,null).statusCode()).isEqualTo(401);
        String refreshed = login("w78_reader","Newpass123!");
        assertThat(send("DELETE","/province/admin/users/"+userId,admin,
                Map.of("expectedVersion",3)).statusCode()).isEqualTo(200);
        assertThat(send("GET","/auth/me",refreshed,null).statusCode()).isEqualTo(401);
        assertThat(send("POST","/province/admin/users",admin,
                Map.of("orgId","199000","username","w78_reader",
                        "displayName","新人员","initialPassword","Password123!",
                        "functionalRoleIds",new String[]{roleId})).statusCode()).isEqualTo(409);

        var lastAdmin = send("PUT","/province/admin/users/199010/status",admin,
                Map.of("enabled",false,"expectedVersion",1));
        assertThat(lastAdmin.statusCode()).isEqualTo(409);
        assertThat(json.readTree(lastAdmin.body()).path("data").path("reasonCode").asText())
                .isEqualTo("LAST_ADMIN");
        assertThat(send("POST","/province/admin/roles/4/replace-and-delete",admin,
                Map.of("expectedVersion",1)).statusCode()).isEqualTo(409);

        String firstCityRole = json.readTree(send("POST","/province/admin/roles",admin,
                Map.of("name","市级查看甲","level","CITY",
                        "permissionCodes",new String[]{"CITY.REPORT.READ"})).body())
                .path("data").path("id").asText();
        String replacementCityRole = json.readTree(send("POST","/province/admin/roles",admin,
                Map.of("name","市级查看乙","level","CITY",
                        "permissionCodes",new String[]{"CITY.REPORT.READ"})).body())
                .path("data").path("id").asText();
        var cityCreated = send("POST","/province/admin/users",admin,
                Map.of("orgId","199001","username","w78_city_reader",
                        "displayName","市级只读","initialPassword","Password123!",
                        "functionalRoleIds",new String[]{firstCityRole}));
        assertThat(cityCreated.statusCode()).isEqualTo(201);
        String cityUserId=json.readTree(cityCreated.body()).path("data").path("id").asText();
        String cityReader=login("w78_city_reader","Password123!");
        assertThat(send("GET","/city/reports",cityReader,null).statusCode()).isEqualTo(200);
        assertThat(send("POST","/city/reports/1/approve",cityReader,
                Map.of("expectedVersion",1)).statusCode()).isEqualTo(403);
        var needsReplacement=send("POST","/province/admin/roles/"+firstCityRole
                +"/replace-and-delete",admin,Map.of("expectedVersion",1));
        assertThat(needsReplacement.statusCode()).isEqualTo(409);
        assertThat(json.readTree(needsReplacement.body()).path("data").path("reasonCode").asText())
                .isEqualTo("ROLE_REPLACEMENT_REQUIRED");
        var replaced=send("POST","/province/admin/roles/"+firstCityRole
                +"/replace-and-delete",admin,Map.of("expectedVersion",1,
                        "replacementRoleId",replacementCityRole));
        assertThat(replaced.statusCode()).isEqualTo(200);
        assertThat(json.readTree(replaced.body()).path("data").path("affectedUserCount").asInt())
                .isEqualTo(1);
        var cityDetail=call("GET","/province/admin/users/"+cityUserId,admin,null)
                .path("data");
        assertThat(cityDetail.path("version").asInt()).isEqualTo(2);
        assertThat(cityDetail.path("functionalRoles").get(0).path("id").asText())
                .isEqualTo(replacementCityRole);
        assertThat(send("GET","/city/reports",cityReader,null).statusCode()).isEqualTo(200);

        var submitter=send("POST","/province/admin/users",admin,
                Map.of("orgId","199002","username","w78_submitter",
                        "displayName","报表提交人","initialPassword","Password123!",
                        "functionalRoleIds",new String[]{"6"}));
        assertThat(submitter.statusCode()).isEqualTo(201);
        String submitterId=json.readTree(submitter.body()).path("data").path("id").asText();
        jdbc.update("""
                INSERT INTO enterprise_report (id,org_id,period_code,status,current_version,
                submission_version,baseline_employment_count,period_employment_count,
                other_reason,updated_at,submitted_at)
                VALUES (199020,199002,'DEMO_2026_10','SUBMITTED_CITY',1,1,10,9,'测试',
                CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO report_snapshot (report_id,submission_version,
                baseline_employment_count,period_employment_count,other_reason,
                submitted_at,submitted_by,result)
                VALUES (199020,1,10,9,'测试',CURRENT_TIMESTAMP,?,'SUBMITTED_CITY')
                """,Long.parseLong(submitterId));
        var historyConflict=send("DELETE","/province/admin/users/"+submitterId,admin,
                Map.of("expectedVersion",1));
        assertThat(historyConflict.statusCode()).isEqualTo(409);
        assertThat(json.readTree(historyConflict.body()).path("data")
                .path("historyTypes").toString()).contains("REPORT_SUBMISSION");
        assertThat(send("PUT","/province/admin/users/"+submitterId+"/status",admin,
                Map.of("enabled",false,"expectedVersion",1)).statusCode()).isEqualTo(200);
    }

    private void seed() {
        jdbc.update("INSERT INTO region VALUES ('W78_P',NULL,'测试省',1)");
        jdbc.update("INSERT INTO region VALUES ('W78_C','W78_P','测试市',2)");
        jdbc.update("INSERT INTO region VALUES ('W78_D','W78_C','测试区',3)");
        jdbc.update("INSERT INTO org_unit VALUES (199000,NULL,'PROVINCE','W78_P','测试省级',true)");
        jdbc.update("INSERT INTO org_unit VALUES (199001,199000,'CITY','W78_C','测试市级',true)");
        jdbc.update("INSERT INTO org_unit VALUES (199002,199001,'ENTERPRISE','W78_D','测试企业',true)");
        String hash=passwords.encode("Password123!");
        jdbc.update("""
                INSERT INTO user_account (id,org_id,username,password_hash,display_name,
                enabled,created_at) VALUES (199010,199000,'w78_admin',?,'省管理员',true,CURRENT_TIMESTAMP)
                """,hash);
        jdbc.update("""
                INSERT INTO user_account (id,org_id,username,password_hash,display_name,
                enabled,created_at) VALUES (199011,199001,'w78_city',?,'市人员',true,CURRENT_TIMESTAMP)
                """,hash);
        jdbc.update("INSERT INTO user_role VALUES (199010,3),(199010,4),(199011,2),(199011,5)");
    }

    private String login(String username,String password) throws Exception {
        var response=send("POST","/auth/login",null,Map.of("username",username,"password",password));
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).path("data").path("accessToken").asText();
    }

    private JsonNode call(String method,String path,String token,Object body) throws Exception {
        return json.readTree(send(method,path,token,body).body());
    }

    private HttpResponse<String> send(String method,String path,String token,Object body)
            throws Exception {
        HttpRequest.Builder request=HttpRequest.newBuilder(URI.create(
                "http://127.0.0.1:"+port+"/api/v1"+path));
        if (token!=null) request.header("Authorization","Bearer "+token);
        if (body!=null) request.header("Content-Type","application/json");
        String payload=body==null?"":json.writeValueAsString(body);
        request.method(method,body==null?HttpRequest.BodyPublishers.noBody()
                :HttpRequest.BodyPublishers.ofString(payload));
        return http.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
}
