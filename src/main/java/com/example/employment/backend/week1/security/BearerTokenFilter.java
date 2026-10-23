package com.example.employment.backend.week1.security;

import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week1.identity.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

public class BearerTokenFilter extends OncePerRequestFilter {
    private final TokenService tokens;
    private final UserRepository users;
    private final SecurityErrors errors;

    public BearerTokenFilter(TokenService tokens, UserRepository users, SecurityErrors errors) {
        this.tokens = tokens;
        this.users = users;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equals(request.getMethod()) || "/api/v1/health".equals(path)
                || "/api/v1/auth/login".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null) {
            chain.doFilter(request, response);
            return;
        }
        if (!header.startsWith("Bearer ")) {
            errors.write(response, 401, "UNAUTHORIZED", "未登录或登录已过期");
            return;
        }
        UserAccount account;
        try {
            long userId = tokens.userId(header.substring(7));
            account = users.findById(userId).orElse(null);
        } catch (JwtException | IllegalArgumentException exception) {
            errors.write(response, 401, "UNAUTHORIZED", "未登录或登录已过期");
            return;
        }
        if (account == null || !account.canLogin()) {
            errors.write(response, 401, "UNAUTHORIZED", "未登录或登录已过期");
            return;
        }
        var authorities = account.roleCodes().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
        var authentication = new UsernamePasswordAuthenticationToken(account, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
