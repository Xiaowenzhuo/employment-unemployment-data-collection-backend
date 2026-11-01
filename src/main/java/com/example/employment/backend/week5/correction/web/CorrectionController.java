package com.example.employment.backend.week5.correction.web;

import com.example.employment.backend.common.ApiResponse;
import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.shared.web.StrictBody;
import com.example.employment.backend.week5.correction.service.CorrectionService;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/province/reports/{reportId}")
public class CorrectionController {
    private final CorrectionService service;
    public CorrectionController(CorrectionService service) { this.service = service; }
    @GetMapping("/corrections")
    public ApiResponse<?> detail(Authentication auth, @PathVariable String reportId) {
        return ApiResponse.ok(service.detail(user(auth), reportId));
    }
    @PostMapping("/corrections")
    public ApiResponse<?> correct(Authentication auth, @PathVariable String reportId,
            @RequestBody Map<String, Object> body) {
        StrictBody.only(body, "expectedVersion", "submissionVersion", "reason", "changes");
        Object changes = body.get("changes");
        if (!(changes instanceof Map<?, ?> raw)) throw new FieldErrorException(
                Map.of("changes", "应为字段对象"));
        for (Object key : raw.keySet()) if (!(key instanceof String))
            throw new FieldErrorException(Map.of("changes", "字段名不合法"));
        @SuppressWarnings("unchecked") Map<String, Object> values = (Map<String, Object>) raw;
        return ApiResponse.ok(service.correct(user(auth), reportId,
                StrictBody.integer(body, "expectedVersion"),
                StrictBody.integer(body, "submissionVersion"),
                StrictBody.string(body, "reason"), values));
    }
    @PostMapping("/void")
    public ApiResponse<?> voidReport(Authentication auth, @PathVariable String reportId,
            @RequestBody Map<String, Object> body) {
        StrictBody.only(body, "expectedVersion", "submissionVersion", "reason");
        return ApiResponse.ok(service.voidReport(user(auth), reportId,
                StrictBody.integer(body, "expectedVersion"),
                StrictBody.integer(body, "submissionVersion"),
                StrictBody.string(body, "reason")));
    }
    private static UserAccount user(Authentication auth) { return (UserAccount) auth.getPrincipal(); }
}
