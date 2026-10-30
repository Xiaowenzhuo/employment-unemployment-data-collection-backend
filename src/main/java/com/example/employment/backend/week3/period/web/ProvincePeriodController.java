package com.example.employment.backend.week3.period.web;

import com.example.employment.backend.common.ApiResponse;
import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.shared.web.StrictBody;
import com.example.employment.backend.week3.period.model.PeriodViews.PeriodInput;
import com.example.employment.backend.week3.period.service.PeriodService;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/province/periods")
public class ProvincePeriodController {
    private final PeriodService periods;

    public ProvincePeriodController(PeriodService periods) { this.periods = periods; }

    @GetMapping
    public ApiResponse<?> list(Authentication auth) {
        return ApiResponse.ok(periods.provinceList(user(auth)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<?>> create(Authentication auth,
            @RequestBody Map<String, Object> body) {
        StrictBody.only(body, "code", "label", "startAt", "endExclusiveAt", "openAt", "closeAt", "demo");
        if (!(body.get("demo") instanceof Boolean demo)) throw field("demo", "应为布尔值");
        PeriodInput input = new PeriodInput(StrictBody.string(body, "code"),
                StrictBody.string(body, "label"), instant(body, "startAt"),
                instant(body, "endExclusiveAt"), instant(body, "openAt"),
                instant(body, "closeAt"), demo, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(periods.create(user(auth), input)));
    }

    @PutMapping("/{code}")
    public ApiResponse<?> update(Authentication auth, @PathVariable String code,
            @RequestBody Map<String, Object> body) {
        StrictBody.only(body, "expectedVersion", "label", "startAt", "endExclusiveAt", "openAt", "closeAt");
        PeriodInput input = new PeriodInput(null, StrictBody.string(body, "label"),
                instant(body, "startAt"), instant(body, "endExclusiveAt"),
                instant(body, "openAt"), instant(body, "closeAt"), null,
                StrictBody.integer(body, "expectedVersion"));
        return ApiResponse.ok(periods.update(user(auth), code, input));
    }

    private static Instant instant(Map<String, Object> body, String field) {
        String value = StrictBody.string(body, field);
        if (value == null || !value.endsWith("Z")) throw field(field, "应为UTC时间，如2026-11-01T00:00:00Z");
        try { return Instant.parse(value).truncatedTo(ChronoUnit.MILLIS); }
        catch (DateTimeParseException exception) { throw field(field, "时间格式不正确"); }
    }

    private static FieldErrorException field(String name, String message) {
        return new FieldErrorException(Map.of(name, message));
    }

    private static UserAccount user(Authentication auth) { return (UserAccount) auth.getPrincipal(); }
}
