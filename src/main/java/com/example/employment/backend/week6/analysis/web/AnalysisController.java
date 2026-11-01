package com.example.employment.backend.week6.analysis.web;

import com.example.employment.backend.common.ApiResponse;
import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week6.analysis.service.AnalysisService;
import com.example.employment.backend.week6.analysis.service.AnalysisService.Filters;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/province/analysis")
public class AnalysisController {
    private static final Set<String> ATTRS = Set.of("regionCode", "natureParentCode",
            "natureCode", "industryParentCode", "industryCode");
    private final AnalysisService service;
    public AnalysisController(AnalysisService service) { this.service = service; }

    @GetMapping("/cities")
    public ApiResponse<?> cities(Authentication auth, @RequestParam Map<String, String> params) {
        only(params, Set.of("periodCode", "regionCode"));
        return ApiResponse.ok(service.cities(user(auth), params.get("periodCode"),
                optional(params, "regionCode")));
    }
    @GetMapping("/compare")
    public ApiResponse<?> compare(Authentication auth, @RequestParam Map<String, String> params) {
        only(params, with("firstPeriodCode", "secondPeriodCode"));
        return ApiResponse.ok(service.compare(user(auth), params.get("firstPeriodCode"),
                params.get("secondPeriodCode"), filters(params)));
    }
    @GetMapping("/trend")
    public ApiResponse<?> trend(Authentication auth, @RequestParam Map<String, String> params) {
        only(params, with("periodCodes"));
        return ApiResponse.ok(service.trend(user(auth), params.get("periodCodes"), filters(params)));
    }
    private static Filters filters(Map<String, String> p) {
        return new Filters(optional(p,"regionCode"),optional(p,"natureParentCode"),
                optional(p,"natureCode"),optional(p,"industryParentCode"),
                optional(p,"industryCode"));
    }
    private static String optional(Map<String, String> p, String key) {
        String value = p.get(key);
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
    private static Set<String> with(String... names) {
        var result = new java.util.HashSet<>(ATTRS);
        result.addAll(Set.of(names));
        return result;
    }
    private static void only(Map<String, String> params, Set<String> allowed) {
        for (String key : params.keySet()) if (!allowed.contains(key))
            throw new FieldErrorException(Map.of(key, "不支持该筛选字段"));
    }
    private static UserAccount user(Authentication auth) { return (UserAccount)auth.getPrincipal(); }
}
