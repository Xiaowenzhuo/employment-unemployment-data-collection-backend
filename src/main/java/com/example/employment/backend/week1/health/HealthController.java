package com.example.employment.backend.week1.health;

import com.example.employment.backend.common.ApiResponse;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final String version;

    public HealthController(@Value("${app.build-version}") String version) {
        this.version = version;
    }

    @GetMapping("/api/v1/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of("status", "UP", "version", version));
    }
}
