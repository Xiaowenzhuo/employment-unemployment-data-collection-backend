package com.example.employment.backend.week5.accounts.web;

import com.example.employment.backend.common.ApiResponse;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week5.accounts.service.ProvinceAccountService;
import com.example.employment.backend.week5.data.web.ProvinceDataController;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/province/accounts")
public class ProvinceAccountQueryController {
    private final ProvinceAccountService service;
    public ProvinceAccountQueryController(ProvinceAccountService service) { this.service = service; }
    @GetMapping
    public ApiResponse<?> list(Authentication auth, @RequestParam Map<String, String> params) {
        int page = ProvinceDataController.integer(params, "page", 0);
        int size = ProvinceDataController.integer(params, "size", 20);
        Map<String, String> filters = new LinkedHashMap<>(params);
        filters.remove("page"); filters.remove("size");
        return ApiResponse.ok(service.page(service.search((UserAccount)auth.getPrincipal(), filters),
                page, size));
    }
}
