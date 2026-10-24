package com.example.employment.backend.common;

import java.util.Map;

public class FieldErrorException extends RuntimeException {
    private final Map<String, String> fieldErrors;

    public FieldErrorException(Map<String, String> fieldErrors) {
        super("请求参数不合法");
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }
}
