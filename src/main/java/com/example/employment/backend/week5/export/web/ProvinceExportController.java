package com.example.employment.backend.week5.export.web;

import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week5.accounts.service.ProvinceAccountService;
import com.example.employment.backend.week5.data.service.ProvinceDataService;
import com.example.employment.backend.week5.data.web.ProvinceDataController;
import com.example.employment.backend.week5.export.service.ExcelExportService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/province")
public class ProvinceExportController {
    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final ProvinceDataService data;
    private final ProvinceAccountService accounts;
    private final ExcelExportService excel;
    public ProvinceExportController(ProvinceDataService data, ProvinceAccountService accounts,
            ExcelExportService excel) { this.data=data; this.accounts=accounts; this.excel=excel; }
    @GetMapping("/exports/reports")
    public ResponseEntity<byte[]> reports(Authentication auth, @RequestParam Map<String,String> params) {
        ProvinceDataController.only(params, "periodCode", "regionCode");
        var set = data.load(user(auth), params.get("periodCode"), params.get("regionCode"));
        return file("企业报表_" + set.period().code() + ".xlsx", excel.reports(set));
    }
    @GetMapping("/exports/enterprise-info")
    public ResponseEntity<byte[]> info(Authentication auth, @RequestParam Map<String,String> params) {
        ProvinceDataController.only(params, "periodCode", "regionCode");
        var set = data.load(user(auth), params.get("periodCode"), params.get("regionCode"));
        return file("企业信息_" + set.period().code() + ".xlsx", excel.enterpriseInfo(set));
    }
    @GetMapping("/accounts/export")
    public ResponseEntity<byte[]> accounts(Authentication auth, @RequestParam Map<String,String> params) {
        for (String key : params.keySet()) if (!ProvinceAccountService.FILTERS.contains(key))
            throw new FieldErrorException(Map.of(key, "不支持该筛选字段"));
        var rows = accounts.search(user(auth), params).items();
        String today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.BASIC_ISO_DATE);
        return file("账号查询_" + today + ".xlsx", excel.accounts(rows));
    }
    private static ResponseEntity<byte[]> file(String name, byte[] bytes) {
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''"
                        + URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20"))
                .body(bytes);
    }
    private static UserAccount user(Authentication auth) { return (UserAccount)auth.getPrincipal(); }
}
