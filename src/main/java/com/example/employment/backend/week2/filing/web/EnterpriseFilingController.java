package com.example.employment.backend.week2.filing.web;

import com.example.employment.backend.common.ApiResponse;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.filing.model.FilingViews.*;
import com.example.employment.backend.week2.filing.model.Profile;
import com.example.employment.backend.week2.filing.service.FilingService;
import com.example.employment.backend.week2.shared.web.StrictBody;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/enterprise")
public class EnterpriseFilingController {
    private final FilingService filings;

    public EnterpriseFilingController(FilingService filings) {
        this.filings = filings;
    }

    @GetMapping("/filing/options")
    public ApiResponse<OptionsData> options(Authentication auth) {
        return ApiResponse.ok(filings.options(user(auth)));
    }

    @GetMapping("/profile")
    public ApiResponse<ProfileData> profile(Authentication auth) {
        return ApiResponse.ok(filings.profile(user(auth)));
    }

    @PutMapping("/profile")
    public ApiResponse<ProfileData> save(Authentication auth, @RequestBody Map<String, Object> body) {
        StrictBody.only(body, "organizationCode", "enterpriseName", "natureParentCode",
                "natureCode", "industryParentCode", "industryCode", "mainBusiness",
                "contactName", "addressParentCode", "addressCode", "postalCode",
                "contactPhone", "fax", "email");
        Profile input = new Profile(StrictBody.string(body, "organizationCode"),
                StrictBody.string(body, "enterpriseName"),
                StrictBody.string(body, "natureParentCode"), StrictBody.string(body, "natureCode"),
                StrictBody.string(body, "industryParentCode"), StrictBody.string(body, "industryCode"),
                StrictBody.string(body, "mainBusiness"), StrictBody.string(body, "contactName"),
                StrictBody.string(body, "addressParentCode"), StrictBody.string(body, "addressCode"),
                StrictBody.string(body, "postalCode"), StrictBody.string(body, "contactPhone"),
                StrictBody.string(body, "fax"), StrictBody.string(body, "email"));
        return ApiResponse.ok(filings.save(user(auth), input));
    }

    @GetMapping("/filing")
    public ApiResponse<StatusData> status(Authentication auth) {
        return ApiResponse.ok(filings.status(user(auth)));
    }

    @GetMapping("/filing/versions/{version}")
    public ApiResponse<VersionData> version(Authentication auth, @PathVariable int version) {
        return ApiResponse.ok(filings.ownVersion(user(auth), version));
    }

    @PostMapping("/filing/submit")
    public ApiResponse<SubmitData> submit(Authentication auth, @RequestBody Map<String, Object> body) {
        StrictBody.only(body, "expectedVersion");
        return ApiResponse.ok(filings.submit(user(auth), StrictBody.integer(body, "expectedVersion")));
    }

    private UserAccount user(Authentication auth) {
        return (UserAccount) auth.getPrincipal();
    }

}
