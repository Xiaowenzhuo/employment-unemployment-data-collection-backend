package com.example.employment.backend.cube.service;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class CubeException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Map<String,String> fields;
    public CubeException(HttpStatus status,String code,String message,Map<String,String> fields) {
        super(message);this.status=status;this.code=code;this.fields=fields;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public Map<String,String> fields() { return fields; }
}
