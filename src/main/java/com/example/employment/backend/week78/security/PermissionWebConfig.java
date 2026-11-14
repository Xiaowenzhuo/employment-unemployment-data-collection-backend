package com.example.employment.backend.week78.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PermissionWebConfig implements WebMvcConfigurer {
    private final PermissionInterceptor permissions;

    public PermissionWebConfig(PermissionInterceptor permissions) {
        this.permissions = permissions;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(permissions).addPathPatterns("/api/v1/**");
    }
}
