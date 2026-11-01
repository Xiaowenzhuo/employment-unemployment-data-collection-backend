package com.example.employment.backend.week5.data.web;

import com.example.employment.backend.common.ApiResponse;
import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week5.data.service.ProvinceDataService;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/province")
public class ProvinceDataController {
    private final ProvinceDataService service;
    public ProvinceDataController(ProvinceDataService service) { this.service = service; }
    @GetMapping("/summaries")
    public ApiResponse<?> summary(Authentication auth, @RequestParam Map<String, String> params) {
        only(params, "periodCode", "regionCode");
        return ApiResponse.ok(service.summary(service.load(user(auth),
                params.get("periodCode"), params.get("regionCode"))));
    }
    @GetMapping("/data/reports")
    public ApiResponse<?> reports(Authentication auth, @RequestParam Map<String, String> params) {
        only(params, "periodCode", "regionCode", "page", "size");
        return ApiResponse.ok(service.reports(service.load(user(auth), params.get("periodCode"),
                params.get("regionCode")), integer(params, "page", 0), integer(params, "size", 20)));
    }
    @GetMapping("/data/enterprise-info")
    public ApiResponse<?> info(Authentication auth, @RequestParam Map<String, String> params) {
        only(params, "periodCode", "regionCode", "page", "size");
        return ApiResponse.ok(service.enterpriseInfo(service.load(user(auth), params.get("periodCode"),
                params.get("regionCode")), integer(params, "page", 0), integer(params, "size", 20)));
    }
    public static void only(Map<String, String> params, String... allowed) {
        for (String key : params.keySet()) if (!Set.of(allowed).contains(key))
            throw new FieldErrorException(Map.of(key, "不支持该筛选字段"));
    }
    public static int integer(Map<String, String> params, String key, int fallback) {
        if (!params.containsKey(key)) return fallback;
        try { return Integer.parseInt(params.get(key)); }
        catch (NumberFormatException ex) { throw new FieldErrorException(Map.of(key, "应为整数")); }
    }
    private static UserAccount user(Authentication auth) { return (UserAccount)auth.getPrincipal(); }
}
