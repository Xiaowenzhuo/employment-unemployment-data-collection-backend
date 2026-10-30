package com.example.employment.backend.week4.review.web;

import com.example.employment.backend.common.ApiResponse;
import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.shared.web.StrictBody;
import com.example.employment.backend.week4.review.service.ReviewService;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/{level:city|province}/reports")
public class ReviewController {
    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) { this.reviews = reviews; }

    @GetMapping
    public ApiResponse<?> list(Authentication auth, @PathVariable String level,
            @RequestParam Map<String, String> filters,
            @RequestParam(required = false) String periodCode,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size) {
        for (String key : filters.keySet()) {
            if (!Set.of("periodCode", "status", "page", "size").contains(key)) {
                throw new FieldErrorException(Map.of(key, "不支持该筛选字段"));
            }
        }
        return ApiResponse.ok(reviews.list(user(auth), level.toUpperCase(), periodCode,
                status, integer(page, "page"), integer(size, "size")));
    }

    @GetMapping("/{reportId}")
    public ApiResponse<?> detail(Authentication auth, @PathVariable String level,
            @PathVariable String reportId) {
        return ApiResponse.ok(reviews.detail(user(auth), level.toUpperCase(), reportId));
    }

    @PostMapping("/{reportId}/approve")
    public ApiResponse<?> approve(Authentication auth, @PathVariable String level,
            @PathVariable String reportId, @RequestBody Map<String, Object> body) {
        StrictBody.only(body, "expectedVersion", "submissionVersion");
        return ApiResponse.ok(reviews.decide(user(auth), level.toUpperCase(), reportId,
                "APPROVE", StrictBody.integer(body, "expectedVersion"),
                StrictBody.integer(body, "submissionVersion"), null));
    }

    @PostMapping("/{reportId}/return")
    public ApiResponse<?> returned(Authentication auth, @PathVariable String level,
            @PathVariable String reportId, @RequestBody Map<String, Object> body) {
        StrictBody.only(body, "expectedVersion", "submissionVersion", "comment");
        return ApiResponse.ok(reviews.decide(user(auth), level.toUpperCase(), reportId,
                "RETURN", StrictBody.integer(body, "expectedVersion"),
                StrictBody.integer(body, "submissionVersion"), StrictBody.string(body, "comment")));
    }

    private static UserAccount user(Authentication auth) { return (UserAccount) auth.getPrincipal(); }

    private static int integer(String value, String field) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) {
            throw new FieldErrorException(Map.of(field, "应为整数"));
        }
    }
}
