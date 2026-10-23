package com.example.employment.backend.week1.identity.service;

import com.example.employment.backend.week1.identity.model.LoginResult;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week1.identity.model.UserSummary;
import com.example.employment.backend.week1.identity.repository.UserRepository;
import com.example.employment.backend.week1.security.TokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;

    public AuthService(UserRepository users, PasswordEncoder passwords, TokenService tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    public LoginResult login(String username, String password) {
        UserAccount account = users.findByUsername(username)
                .orElseThrow(InvalidCredentialsException::new);
        if (!account.canLogin() || !passwords.matches(password, account.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        return new LoginResult(tokens.issue(account.id()), TokenService.TTL_SECONDS,
                UserSummary.from(account));
    }

    public static class InvalidCredentialsException extends RuntimeException {
    }
}
