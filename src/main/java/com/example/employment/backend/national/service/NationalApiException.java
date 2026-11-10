package com.example.employment.backend.national.service;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

public class NationalApiException extends RuntimeException {
    private final HttpStatus status;
    private final Map<String, Object> data;

    public NationalApiException(HttpStatus status, String message, String reasonCode,
            Map<String, Object> details) {
        super(message);
        this.status = status;
        this.data = new LinkedHashMap<>();
        this.data.put("reasonCode", reasonCode);
        this.data.putAll(details);
    }
    public HttpStatus status() { return status; }
    public Map<String, Object> data() { return data; }
}
