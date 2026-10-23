package com.example.employment.backend.week1.security;

import com.example.employment.backend.common.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

public class SecurityErrors {
    private final JsonMapper json;

    public SecurityErrors(JsonMapper json) {
        this.json = json;
    }

    public void write(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(json.writeValueAsString(ApiResponse.error(code, message)));
    }
}
