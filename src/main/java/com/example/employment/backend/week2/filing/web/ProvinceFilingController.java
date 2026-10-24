package com.example.employment.backend.week2.filing.web;

import com.example.employment.backend.common.ApiResponse;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.filing.model.FilingViews.*;
import com.example.employment.backend.week2.filing.service.FilingExportService;
import com.example.employment.backend.week2.filing.service.FilingService;
import com.example.employment.backend.week2.shared.web.StrictBody;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/province/filings")
public class ProvinceFilingController {
    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final FilingService filings;
    private final FilingExportService exports;

    public ProvinceFilingController(FilingService filings, FilingExportService exports) {
        this.filings = filings;
        this.exports = exports;
    }

    @GetMapping("/periods")
    public ApiResponse<List<Period>> periods(Authentication auth) {
        return ApiResponse.ok(filings.periods(user(auth)));
    }

    @GetMapping
    public ApiResponse<ListData> list(Authentication auth,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) String periodCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(filings.list(user(auth), status, regionCode, periodCode, page, size));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(Authentication auth,
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) String periodCode) {
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=approved-filings.xlsx")
                .body(exports.export(user(auth), regionCode, periodCode));
    }

    @GetMapping("/{filingId}")
    public ApiResponse<DetailData> detail(Authentication auth, @PathVariable long filingId) {
        return ApiResponse.ok(filings.detail(user(auth), filingId));
    }

    @GetMapping("/{filingId}/versions/{version}")
    public ApiResponse<VersionData> version(Authentication auth, @PathVariable long filingId,
            @PathVariable int version) {
        return ApiResponse.ok(filings.provinceVersion(user(auth), filingId, version));
    }

    @PostMapping("/{filingId}/approve")
    public ApiResponse<ReviewData> approve(Authentication auth, @PathVariable long filingId,
            @RequestBody Map<String, Object> body) {
        StrictBody.only(body, "expectedVersion");
        return ApiResponse.ok(filings.review(user(auth), filingId,
                StrictBody.integer(body, "expectedVersion"), true, null));
    }

    @PostMapping("/{filingId}/return")
    public ApiResponse<ReviewData> returnFiling(Authentication auth, @PathVariable long filingId,
            @RequestBody Map<String, Object> body) {
        StrictBody.only(body, "expectedVersion", "reason");
        return ApiResponse.ok(filings.review(user(auth), filingId,
                StrictBody.integer(body, "expectedVersion"), false,
                StrictBody.string(body, "reason")));
    }

    private UserAccount user(Authentication auth) {
        return (UserAccount) auth.getPrincipal();
    }

}
