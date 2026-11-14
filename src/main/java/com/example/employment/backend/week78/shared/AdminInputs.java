package com.example.employment.backend.week78.shared;

import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.week2.shared.web.StrictBody;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public final class AdminInputs {
    private AdminInputs() {
    }

    public static String text(Map<String, Object> body, String key, int max) {
        String value = StrictBody.string(body, key);
        if (value == null || value.trim().isEmpty() || value.trim().length() > max) {
            throw field(key, "不能为空且最多" + max + "字");
        }
        return value.trim();
    }

    public static long id(String value, String field) {
        if (value == null || !value.matches("[0-9]+")) throw field(field, "无效 ID");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw field(field, "无效 ID");
        }
    }

    public static long version(Map<String, Object> body) {
        Object value = body.get("expectedVersion");
        if (!(value instanceof Number number) || number.longValue() < 1
                || number.doubleValue() != number.longValue()) {
            throw field("expectedVersion", "必须是正整数");
        }
        return number.longValue();
    }

    public static boolean enabled(Map<String, Object> body) {
        if (!(body.get("enabled") instanceof Boolean value)) throw field("enabled", "必须是布尔值");
        return value;
    }

    public static List<String> strings(Map<String, Object> body, String key) {
        if (!(body.get(key) instanceof List<?> raw) || raw.isEmpty()
                || raw.stream().anyMatch(v -> !(v instanceof String))) {
            throw field(key, "至少选择一项");
        }
        List<String> values = raw.stream().map(String.class::cast).toList();
        if (new HashSet<>(values).size() != values.size()) throw field(key, "不能重复");
        return values;
    }

    public static String password(Map<String, Object> body, String key) {
        String value = StrictBody.string(body, key);
        if (value == null || !value.matches("^(?=.*[A-Za-z])(?=.*[0-9])[!-~]{8,64}$")) {
            throw field(key, "密码需为8至64位可见ASCII字符，且包含字母和数字");
        }
        return value;
    }

    public static int page(Integer value) {
        if (value == null) return 0;
        if (value < 0) throw field("page", "不能小于0");
        return value;
    }

    public static int size(Integer value) {
        if (value == null) return 20;
        if (value < 1 || value > 100) throw field("size", "必须为1至100");
        return value;
    }

    public static FieldErrorException field(String key, String message) {
        return new FieldErrorException(Map.of(key, message));
    }
}
