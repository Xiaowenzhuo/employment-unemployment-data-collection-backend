package com.example.employment.backend.week78.security;

import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week1.security.SecurityErrors;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class PermissionInterceptor implements HandlerInterceptor {
    private final JdbcTemplate jdbc;
    private final SecurityErrors errors;
    private volatile List<Route> routes;

    public PermissionInterceptor(JdbcTemplate jdbc, SecurityErrors errors) {
        this.jdbc = jdbc;
        this.errors = errors;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserAccount account)) {
            return true;
        }
        String path = request.getRequestURI();
        if (!path.startsWith("/api/v1/")) {
            return true;
        }
        String[] actual = path.substring("/api/v1".length()).split("/");
        Route selected = null;
        int best = -1;
        for (Route route : routes()) {
            if (!route.method().equals(request.getMethod())
                    || !account.roleCodes().contains(route.identity())) {
                continue;
            }
            String[] pattern = route.path().split("/");
            if (pattern.length != actual.length) {
                continue;
            }
            int score = 0;
            boolean matches = true;
            for (int i = 0; i < pattern.length; i++) {
                if (pattern[i].startsWith("{") && pattern[i].endsWith("}")) {
                    continue;
                }
                if (!pattern[i].equals(actual[i])) {
                    matches = false;
                    break;
                }
                score++;
            }
            if (matches && score > best) {
                selected = route;
                best = score;
            }
        }
        if (selected != null && !account.permissions().contains(selected.permission())) {
            errors.write(response, 403, "FORBIDDEN", "无权访问");
            return false;
        }
        return true;
    }

    private List<Route> routes() {
        List<Route> current = routes;
        if (current == null) {
            synchronized (this) {
                current = routes;
                if (current == null) {
                    current = jdbc.query("""
                            SELECT http_method,path_pattern,identity_code,permission_code
                            FROM permission_route
                            """, (rs, row) -> new Route(rs.getString(1), rs.getString(2),
                            rs.getString(3), rs.getString(4)));
                    routes = current;
                }
            }
        }
        return current;
    }

    private record Route(String method, String path, String identity, String permission) {
    }
}
