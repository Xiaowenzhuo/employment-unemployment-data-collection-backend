package com.example.employment.backend.week1.identity.web;

import com.example.employment.backend.common.ApiResponse;
import com.example.employment.backend.week1.identity.model.LoginResult;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week1.identity.model.UserSummary;
import com.example.employment.backend.week1.identity.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResult> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(auth.login(request.username(), request.password()));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, UserSummary>> me(Authentication authentication) {
        UserAccount account = (UserAccount) authentication.getPrincipal();
        return ApiResponse.ok(Map.of("user", UserSummary.from(account)));
    }

    public record LoginRequest(@NotBlank @Size(max = 64) String username,
            @NotBlank String password) {
    }
}
