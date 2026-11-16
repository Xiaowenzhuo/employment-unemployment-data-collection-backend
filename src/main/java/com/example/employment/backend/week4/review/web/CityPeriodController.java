package com.example.employment.backend.week4.review.web;

import com.example.employment.backend.common.ApiResponse;
import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week4.review.service.ReviewService;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/city/reports/periods")
public class CityPeriodController {
    private final ReviewService reviews;
    public CityPeriodController(ReviewService reviews) { this.reviews=reviews; }
    @GetMapping
    public ApiResponse<?> periods(Authentication auth,@RequestParam Map<String,String> params) {
        if(!params.isEmpty()) throw new FieldErrorException(Map.of(
                params.keySet().iterator().next(),"不支持该参数"));
        return ApiResponse.success(reviews.cityPeriods((UserAccount)auth.getPrincipal()));
    }
}
