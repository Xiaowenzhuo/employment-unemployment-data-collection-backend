package com.example.employment.backend.revision.web;

import com.example.employment.backend.common.ApiResponse;
import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.revision.service.OverviewService;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class OverviewController {
    private final OverviewService service;
    public OverviewController(OverviewService service) { this.service=service; }
    @GetMapping("/enterprise/work-overview")
    public ApiResponse<?> enterprise(Authentication auth,@RequestParam Map<String,String> params) {
        return ApiResponse.ok(service.enterprise(user(auth),period(params)));
    }
    @GetMapping("/city/work-overview")
    public ApiResponse<?> city(Authentication auth,@RequestParam Map<String,String> params) {
        return ApiResponse.ok(service.city(user(auth),period(params)));
    }
    @GetMapping("/province/work-overview")
    public ApiResponse<?> province(Authentication auth,@RequestParam Map<String,String> params) {
        return ApiResponse.ok(service.province(user(auth),period(params)));
    }
    private static String period(Map<String,String> params) {
        for (String key:params.keySet()) if (!"periodCode".equals(key))
            throw new FieldErrorException(Map.of(key,"不支持该筛选字段"));
        return params.get("periodCode");
    }
    private static UserAccount user(Authentication auth) { return (UserAccount)auth.getPrincipal(); }
}
