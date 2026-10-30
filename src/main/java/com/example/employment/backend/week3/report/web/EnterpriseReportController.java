package com.example.employment.backend.week3.report.web;

import com.example.employment.backend.common.ApiResponse;
import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.shared.web.StrictBody;
import com.example.employment.backend.week3.report.model.ReportForm;
import com.example.employment.backend.week3.report.service.ReportService;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/enterprise/reports")
public class EnterpriseReportController {
    private final ReportService reports;

    public EnterpriseReportController(ReportService reports) { this.reports = reports; }

    @GetMapping("/options")
    public ApiResponse<?> options(Authentication auth) {
        return ApiResponse.ok(reports.options(user(auth)));
    }

    @GetMapping
    public ApiResponse<?> list(Authentication auth,
            @RequestParam Map<String, String> filters,
            @RequestParam(required = false) String periodCode,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size) {
        for (String key : filters.keySet()) {
            if (!Set.of("periodCode", "status", "page", "size").contains(key)) {
                throw new FieldErrorException(Map.of(key, "不支持该筛选字段"));
            }
        }
        return ApiResponse.ok(reports.list(user(auth), periodCode, status,
                integer(page, "page"), integer(size, "size")));
    }

    @GetMapping("/{periodCode}")
    public ApiResponse<?> detail(Authentication auth, @PathVariable String periodCode) {
        return ApiResponse.ok(reports.detail(user(auth), periodCode));
    }

    @PutMapping("/{periodCode}")
    public ApiResponse<?> save(Authentication auth, @PathVariable String periodCode,
            @RequestBody Map<String, Object> body) {
        StrictBody.only(body, "expectedVersion", "baselineEmploymentCount", "periodEmploymentCount",
                "otherReason", "reductionTypeCode", "primaryReasonCode", "primaryReasonDetail",
                "secondaryReasonCode", "secondaryReasonDetail", "tertiaryReasonCode",
                "tertiaryReasonDetail");
        for (String field : new String[] {"expectedVersion", "baselineEmploymentCount",
                "periodEmploymentCount", "otherReason", "reductionTypeCode", "primaryReasonCode",
                "primaryReasonDetail", "secondaryReasonCode", "secondaryReasonDetail",
                "tertiaryReasonCode", "tertiaryReasonDetail"}) {
            if (!body.containsKey(field)) throw new FieldErrorException(Map.of(field, "必须提供该字段，可为null"));
        }
        ReportForm form = new ReportForm(StrictBody.integer(body, "baselineEmploymentCount"),
                StrictBody.integer(body, "periodEmploymentCount"), StrictBody.string(body, "otherReason"),
                StrictBody.string(body, "reductionTypeCode"), StrictBody.string(body, "primaryReasonCode"),
                StrictBody.string(body, "primaryReasonDetail"), StrictBody.string(body, "secondaryReasonCode"),
                StrictBody.string(body, "secondaryReasonDetail"), StrictBody.string(body, "tertiaryReasonCode"),
                StrictBody.string(body, "tertiaryReasonDetail"));
        return ApiResponse.ok(reports.save(user(auth), periodCode,
                StrictBody.integer(body, "expectedVersion"), form));
    }

    @PostMapping("/{periodCode}/submit")
    public ApiResponse<?> submit(Authentication auth, @PathVariable String periodCode,
            @RequestBody Map<String, Object> body) {
        StrictBody.only(body, "expectedVersion");
        return ApiResponse.ok(reports.submit(user(auth), periodCode,
                StrictBody.integer(body, "expectedVersion")));
    }

    @GetMapping("/{periodCode}/history")
    public ApiResponse<?> history(Authentication auth, @PathVariable String periodCode) {
        return ApiResponse.ok(reports.history(user(auth), periodCode));
    }

    private static UserAccount user(Authentication auth) { return (UserAccount) auth.getPrincipal(); }

    private static int integer(String value, String field) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) {
            throw new FieldErrorException(Map.of(field, "应为整数"));
        }
    }
}
