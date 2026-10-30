package com.example.employment.backend.week3.period.web;

import com.example.employment.backend.common.ApiResponse;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week3.period.service.PeriodService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/enterprise/periods")
public class EnterprisePeriodController {
    private final PeriodService periods;

    public EnterprisePeriodController(PeriodService periods) { this.periods = periods; }

    @GetMapping
    public ApiResponse<?> list(Authentication auth) {
        return ApiResponse.ok(periods.enterpriseList((UserAccount) auth.getPrincipal()));
    }
}
