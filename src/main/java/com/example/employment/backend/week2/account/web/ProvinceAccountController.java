package com.example.employment.backend.week2.account.web;

import com.example.employment.backend.common.ApiResponse;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.account.service.AccountService;
import com.example.employment.backend.week2.account.service.AccountService.Created;
import com.example.employment.backend.week2.account.model.CreateAccountRequest;
import com.example.employment.backend.week2.shared.web.StrictBody;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/province/enterprise-accounts")
public class ProvinceAccountController {
    private final AccountService accounts;

    public ProvinceAccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Created>> create(Authentication authentication,
            @RequestBody Map<String, Object> body) {
        StrictBody.only(body, "username", "initialPassword", "orgName", "regionCode");
        var request = new CreateAccountRequest(StrictBody.string(body, "username"),
                StrictBody.string(body, "initialPassword"), StrictBody.string(body, "orgName"),
                StrictBody.string(body, "regionCode"));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                accounts.create((UserAccount) authentication.getPrincipal(), request)));
    }

}
