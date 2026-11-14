package com.example.employment.backend.week78.shared;

import java.util.List;
import java.util.Map;

public class AdminConflictException extends RuntimeException {
    private final Map<String, Object> data;

    public AdminConflictException(String reasonCode, String message) {
        this(reasonCode, message, List.of());
    }

    public AdminConflictException(String reasonCode, String message, List<String> historyTypes) {
        super(message);
        this.data = historyTypes.isEmpty() ? Map.of("reasonCode", reasonCode)
                : Map.of("reasonCode", reasonCode, "historyTypes", historyTypes);
    }

    public Map<String, Object> data() {
        return data;
    }
}
