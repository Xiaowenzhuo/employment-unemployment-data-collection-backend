package com.example.employment.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WeekOneHttpIntegrationTests {
    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwords;

    @Autowired
    JsonMapper json;

    @Value("${app.auth.secret}")
    String testSecret;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void browserRequestsCoverLoginRolesAndRegions() throws Exception {
        seed();

        var health = get("/api/v1/health", null);
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(body(health).path("data").path("status").asText()).isEqualTo("UP");
        assertThat(body(health).path("traceId").asText()).isNotBlank();

        HttpRequest preflight = HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
        var preflightResponse = client.send(preflight, HttpResponse.BodyHandlers.ofString());
        assertThat(preflightResponse.statusCode()).isEqualTo(200);
        assertThat(preflightResponse.headers().firstValue("Access-Control-Allow-Origin"))
                .contains("http://localhost:5173");

        assertThat(get("/api/v1/auth/me", null).statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/meta/regions", null).statusCode()).isEqualTo(401);
        assertThat(login("demo_enterprise", "wrong-password").statusCode()).isEqualTo(401);
        assertThat(login("", "test-password").statusCode()).isEqualTo(400);

        for (String[] account : new String[][] {
                {"demo_enterprise", "ENTERPRISE"}, {"demo_city", "CITY"},
                {"demo_province", "PROVINCE"}}) {
            var response = login(account[0], "test-password");
            assertThat(response.statusCode()).isEqualTo(200);
            JsonNode data = body(response).path("data");
            assertThat(data.path("user").path("roleCode").asText()).isEqualTo(account[1]);
            assertThat(data.path("user").path("id").isString()).isTrue();
            assertThat(data.path("expiresInSeconds").asLong()).isEqualTo(7200);
            String token = data.path("accessToken").asText();
            assertThat(token).isNotBlank();

            var me = get("/api/v1/auth/me", token);
            assertThat(me.statusCode()).isEqualTo(200);
            assertThat(body(me).path("data").path("user").path("roleCode").asText())
                    .isEqualTo(account[1]);
            assertThat(get("/api/v1/meta/regions?parentCode=DEMO_P", token).statusCode())
                    .isEqualTo(200);
            if (!"PROVINCE".equals(account[1])) {
                var denied = get("/api/v1/province/placeholder", token);
                assertThat(denied.statusCode()).isEqualTo(403);
                assertThat(body(denied).path("code").asText()).isEqualTo("FORBIDDEN");
            }
        }

        String token = body(login("demo_enterprise", "test-password"))
                .path("data").path("accessToken").asText();
        assertThat(jdbc.queryForObject("SELECT password_hash FROM user_account WHERE id = 101",
                String.class)).isNotEqualTo("test-password");
        assertThat(get("/api/v1/auth/me", token + "broken").statusCode()).isEqualTo(401);
        var now = Instant.now();
        var expiredClaims = JwtClaimsSet.builder().subject("101")
                .issuedAt(now.minusSeconds(7200)).expiresAt(now.minusSeconds(3600)).build();
        var key = new SecretKeySpec(testSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var expiredToken = new NimbusJwtEncoder(new ImmutableSecret<>(key))
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                        expiredClaims)).getTokenValue();
        assertThat(get("/api/v1/auth/me", expiredToken).statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/meta/regions?parentCode=TOO_LONG_REGION_CODE", token).statusCode())
                .isEqualTo(400);
        assertThat(get("/api/v1/unknown", token).statusCode()).isEqualTo(404);
        assertThat(body(get("/api/v1/meta/regions?parentCode=DEMO_D", token))
                .path("data").isEmpty()).isTrue();
        jdbc.update("UPDATE user_account SET enabled = false WHERE username = ?", "demo_enterprise");
        assertThat(get("/api/v1/auth/me", token).statusCode()).isEqualTo(401);
    }

    private void seed() {
        jdbc.update("INSERT INTO region (code, parent_code, name, level) VALUES (?, ?, ?, ?)",
                "DEMO_P", null, "演示省", 1);
        jdbc.update("INSERT INTO region (code, parent_code, name, level) VALUES (?, ?, ?, ?)",
                "DEMO_C", "DEMO_P", "演示市", 2);
        jdbc.update("INSERT INTO region (code, parent_code, name, level) VALUES (?, ?, ?, ?)",
                "DEMO_D", "DEMO_C", "演示区县", 3);
        jdbc.update("INSERT INTO org_unit VALUES (?, ?, ?, ?, ?, ?)",
                100, null, "PROVINCE", "DEMO_P", "演示省级单位", true);
        jdbc.update("INSERT INTO org_unit VALUES (?, ?, ?, ?, ?, ?)",
                200, 100, "CITY", "DEMO_C", "演示市级单位", true);
        jdbc.update("INSERT INTO org_unit VALUES (?, ?, ?, ?, ?, ?)",
                300, 200, "ENTERPRISE", "DEMO_D", "演示企业", true);
        String hash = passwords.encode("test-password");
        user(101, 300, "demo_enterprise", hash, 1);
        user(102, 200, "demo_city", hash, 2);
        user(103, 100, "demo_province", hash, 3);
    }

    private void user(long id, long orgId, String username, String hash, long roleId) {
        jdbc.update("""
                INSERT INTO user_account
                (id, org_id, username, password_hash, display_name, enabled, created_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, id, orgId, username, hash, username, true);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", id, roleId);
    }

    private HttpResponse<String> login(String username, String password) throws Exception {
        String requestBody = json.writeValueAsString(
                new LoginRequest(username, password));
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private JsonNode body(HttpResponse<String> response) {
        return json.readTree(response.body());
    }

    private record LoginRequest(String username, String password) {
    }
}
