package com.example.employment.backend.week5.data.service;

import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.shared.dao.RegionScopeDao.Org;
import com.example.employment.backend.week2.shared.repository.RegionScopeRepository;
import com.example.employment.backend.week2.shared.service.ScopeService;
import com.example.employment.backend.week3.period.model.SurveyPeriod;
import com.example.employment.backend.week3.period.repository.PeriodRepository;
import com.example.employment.backend.week3.report.model.ReportForm;
import com.example.employment.backend.week5.data.repository.ProvinceDataRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ProvinceDataService {
    private final ProvinceDataRepository data;
    private final ScopeService scopes;
    private final RegionScopeRepository regions;
    private final PeriodRepository periods;
    public ProvinceDataService(ProvinceDataRepository data, ScopeService scopes,
            RegionScopeRepository regions, PeriodRepository periods) {
        this.data = data; this.scopes = scopes; this.regions = regions; this.periods = periods;
    }
    public DataSet load(UserAccount actor, String periodCode, String regionCode) {
        Org province = scopes.province(actor);
        if (periodCode == null || periodCode.isBlank()) throw field("periodCode", "必填");
        SurveyPeriod period = periods.byCode(periodCode, false)
                .orElseThrow(() -> field("periodCode", "调查期不存在"));
        if (period.provinceOrgId() == null || period.provinceOrgId() != province.id())
            throw field("periodCode", "调查期不属于本省");
        List<String> regionCodes = null;
        if (regionCode != null && !regionCode.isBlank()) {
            try {
                scopes.pathWithin(regionCode, province.regionCode());
                regionCodes = regions.descendants(regionCode);
            } catch (RuntimeException ex) { throw field("regionCode", "地区不属于本省"); }
        }
        return new DataSet(period, regionCode, data.rows(province.id(), periodCode, regionCodes));
    }
    public Map<String, Object> summary(DataSet set) {
        Map<String, Map<String, Object>> cities = new LinkedHashMap<>();
        long baseline = 0, current = 0;
        for (var row : set.rows()) {
            int b = number(row, "baseline_employment_count");
            int c = number(row, "period_employment_count");
            baseline += b; current += c;
            String id = id(row, "city_org_id");
            Map<String, Object> city = cities.computeIfAbsent(id, key -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("cityOrgId", key); item.put("cityName", row.get("city_name"));
                item.put("cityRegionCode", row.get("city_region_code"));
                item.put("enterpriseCount", 0L); item.put("baselineEmploymentTotal", 0L);
                item.put("periodEmploymentTotal", 0L); item.put("netChange", 0L);
                return item;
            });
            city.put("enterpriseCount", (Long)city.get("enterpriseCount") + 1);
            city.put("baselineEmploymentTotal", (Long)city.get("baselineEmploymentTotal") + b);
            city.put("periodEmploymentTotal", (Long)city.get("periodEmploymentTotal") + c);
            city.put("netChange", (Long)city.get("periodEmploymentTotal")
                    - (Long)city.get("baselineEmploymentTotal"));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("periodCode", set.period().code());
        result.put("periodLabel", set.period().label());
        result.put("regionCode", set.regionCode());
        result.put("sampleRule", "APPROVED_PROVINCE_NOT_VOIDED_LATEST_SUBMISSION_EFFECTIVE_VALUES");
        result.put("enterpriseCount", (long)set.rows().size());
        result.put("baselineEmploymentTotal", baseline);
        result.put("periodEmploymentTotal", current);
        result.put("netChange", current - baseline);
        result.put("cities", new ArrayList<>(cities.values()));
        return result;
    }
    public Map<String, Object> reports(DataSet set, int page, int size) {
        return page(set, page, size, true);
    }
    public Map<String, Object> enterpriseInfo(DataSet set, int page, int size) {
        return page(set, page, size, false);
    }
    private Map<String, Object> page(DataSet set, int page, int size, boolean reports) {
        if (page < 0 || (long)page * size > Integer.MAX_VALUE) throw field("page", "页码不合法");
        if (size < 1 || size > 100) throw field("size", "每页应为1至100条");
        int from = Math.min(page * size, set.rows().size());
        int to = Math.min(from + size, set.rows().size());
        List<Map<String, Object>> items = set.rows().subList(from, to).stream()
                .map(reports ? this::reportRow : this::infoRow).toList();
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("periodCode", set.period().code()); filters.put("regionCode", set.regionCode());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items); result.put("total", (long)set.rows().size());
        result.put("page", page); result.put("size", size); result.put("filters", filters);
        return result;
    }
    public Map<String, Object> reportRow(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reportId", id(r, "report_id")); m.put("periodCode", r.get("period_code"));
        m.put("submissionVersion", number(r, "submission_version"));
        m.put("currentVersion", number(r, "current_version"));
        m.put("orgId", id(r, "org_id")); m.put("enterpriseName", r.get("enterprise_name"));
        m.put("organizationCode", r.get("organization_code"));
        m.put("cityOrgId", id(r, "city_org_id")); m.put("cityName", r.get("city_name"));
        m.put("regionCode", r.get("region_code")); m.put("regionName", r.get("region_name"));
        m.put("originalForm", form(r, true)); m.put("effectiveForm", form(r, false));
        m.put("correctionCount", number(r, "correction_count"));
        m.put("lastCorrectedAt", utc(r.get("last_corrected_at")));
        return m;
    }
    public Map<String, Object> infoRow(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reportId", id(r, "report_id")); m.put("periodCode", r.get("period_code"));
        m.put("submissionVersion", number(r, "submission_version"));
        m.put("orgId", id(r, "org_id")); m.put("regionCode", r.get("region_code"));
        m.put("regionName", r.get("region_name"));
        boolean frozen = r.get("snapshot_filing_id") != null;
        boolean fallback = !frozen && r.get("current_filing_id") != null;
        m.put("filingId", frozen ? id(r, "snapshot_filing_id") : null);
        m.put("filingVersion", frozen ? number(r, "snapshot_filing_version") : null);
        m.put("filingSource", frozen ? "SNAPSHOT_VERSION" : fallback ? "CURRENT_FALLBACK" : "UNAVAILABLE");
        for (String[] pair : new String[][] {{"organizationCode","organization_code"},
                {"enterpriseName","enterprise_name"}, {"natureCode","nature_code"},
                {"natureName","nature_name"}, {"industryCode","industry_code"},
                {"industryName","industry_name"}, {"mainBusiness","main_business"},
                {"contactName","contact_name"}, {"addressName","address_name"},
                {"postalCode","postal_code"}, {"contactPhone","contact_phone"},
                {"fax","fax"}, {"email","email"}})
            m.put(pair[0], frozen || fallback ? r.get(pair[1]) : null);
        return m;
    }
    private static ReportForm form(Map<String, Object> r, boolean original) {
        String p = original ? "original_" : "";
        return new ReportForm(number(r, p + (original ? "baseline" : "baseline_employment_count")),
                number(r, p + (original ? "period" : "period_employment_count")),
                (String)r.get(p + (original ? "other" : "other_reason")),
                (String)r.get(p + (original ? "type" : "reduction_type_code")),
                (String)r.get(p + (original ? "primary" : "primary_reason_code")),
                (String)r.get(p + (original ? "primary_detail" : "primary_reason_detail")),
                (String)r.get(p + (original ? "secondary" : "secondary_reason_code")),
                (String)r.get(p + (original ? "secondary_detail" : "secondary_reason_detail")),
                (String)r.get(p + (original ? "tertiary" : "tertiary_reason_code")),
                (String)r.get(p + (original ? "tertiary_detail" : "tertiary_reason_detail")));
    }
    private static String id(Map<String, Object> r, String key) {
        Object value = r.get(key); return value == null ? null : value.toString();
    }
    private static int number(Map<String, Object> r, String key) {
        Object value = r.get(key); return value == null ? 0 : ((Number)value).intValue();
    }
    private static java.time.Instant utc(Object value) {
        if (value == null) return null;
        LocalDateTime date = value instanceof java.sql.Timestamp timestamp
                ? timestamp.toLocalDateTime() : (LocalDateTime)value;
        return date.toInstant(ZoneOffset.UTC);
    }
    private static FieldErrorException field(String key, String message) {
        return new FieldErrorException(Map.of(key, message));
    }
    public record DataSet(SurveyPeriod period, String regionCode, List<Map<String, Object>> rows) { }
}
