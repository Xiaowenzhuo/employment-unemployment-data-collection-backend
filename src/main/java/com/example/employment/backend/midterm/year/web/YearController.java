package com.example.employment.backend.midterm.year.web;

import com.example.employment.backend.common.ApiResponse;
import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.midterm.year.service.YearService;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.shared.web.StrictBody;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/province/periods/year")
public class YearController {
    private final YearService years;
    public YearController(YearService years) { this.years=years; }
    @GetMapping("/preview")
    public ApiResponse<?> preview(Authentication auth,@RequestParam Map<String,String> params) {
        if (!params.keySet().equals(java.util.Set.of("year")))
            throw new FieldErrorException(Map.of("year","只接受必填的year参数"));
        return ApiResponse.ok(years.preview(user(auth),parse(params.get("year"))));
    }
    @PostMapping
    public ApiResponse<?> create(Authentication auth,@RequestBody Map<String,Object> body) {
        StrictBody.only(body,"year");
        return ApiResponse.ok(years.create(user(auth),StrictBody.integer(body,"year")));
    }
    private static Integer parse(String value) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException ex) {
            throw new FieldErrorException(Map.of("year","应为整数"));
        }
    }
    private static UserAccount user(Authentication auth) { return (UserAccount)auth.getPrincipal(); }
}
