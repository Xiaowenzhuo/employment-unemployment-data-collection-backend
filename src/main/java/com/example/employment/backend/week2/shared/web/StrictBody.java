package com.example.employment.backend.week2.shared.web;

import com.example.employment.backend.common.FieldErrorException;
import java.util.Map;
import java.util.Set;

public final class StrictBody {
    private StrictBody() {
    }

    public static void only(Map<String, Object> body, String... fields) {
        Set<String> allowed = Set.of(fields);
        for (String key : body.keySet()) {
            if (!allowed.contains(key)) {
                throw field(key, "不支持该字段");
            }
        }
    }

    public static String string(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) return null;
        if (value instanceof String string) return string;
        throw field(key, "应为字符串");
    }

    public static Integer integer(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) return null;
        if (value instanceof Integer integer) return integer;
        throw field(key, "应为整数");
    }

    private static FieldErrorException field(String key, String message) {
        return new FieldErrorException(Map.of(key, message));
    }
}
