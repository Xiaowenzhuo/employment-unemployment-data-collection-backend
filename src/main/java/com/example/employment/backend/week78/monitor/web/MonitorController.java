package com.example.employment.backend.week78.monitor.web;

import com.example.employment.backend.common.ApiResponse;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week78.monitor.service.MonitorService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/province/admin")
public class MonitorController {
    private final MonitorService monitor;

    public MonitorController(MonitorService monitor) {
        this.monitor = monitor;
    }

    @GetMapping("/monitor")
    public ApiResponse<?> current(Authentication authentication) {
        return ApiResponse.ok(monitor.current((UserAccount) authentication.getPrincipal()));
    }
}
