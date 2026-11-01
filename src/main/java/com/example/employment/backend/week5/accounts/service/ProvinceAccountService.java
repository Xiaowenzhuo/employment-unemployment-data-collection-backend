package com.example.employment.backend.week5.accounts.service;

import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.shared.dao.RegionScopeDao.Org;
import com.example.employment.backend.week2.shared.repository.RegionScopeRepository;
import com.example.employment.backend.week2.shared.service.ScopeService;
import com.example.employment.backend.week3.period.model.SurveyPeriod;
import com.example.employment.backend.week3.period.repository.PeriodRepository;
import com.example.employment.backend.week5.accounts.repository.ProvinceAccountRepository;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ProvinceAccountService {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter SQL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final Set<String> FILTERS = Set.of("unitName", "username", "userType",
            "cityCode", "countyCode", "regionCode", "dataStatus", "natureCode",
            "industryCode", "createdFrom", "createdTo", "periodCode", "month",
            "quarter", "reportStatus");
    private final ProvinceAccountRepository accounts;
    private final ScopeService scopes;
    private final RegionScopeRepository regions;
    private final PeriodRepository periods;
    public ProvinceAccountService(ProvinceAccountRepository accounts, ScopeService scopes,
            RegionScopeRepository regions, PeriodRepository periods) {
        this.accounts=accounts; this.scopes=scopes; this.regions=regions; this.periods=periods;
    }
    public Result search(UserAccount actor, Map<String, String> input) {
        Org province = scopes.province(actor);
        Map<String, String> f = new LinkedHashMap<>();
        for (var entry : input.entrySet()) {
            if (!FILTERS.contains(entry.getKey())) throw field(entry.getKey(), "不支持该筛选字段");
            if (entry.getValue() != null && !entry.getValue().trim().isEmpty())
                f.put(entry.getKey(), entry.getValue().trim());
        }
        if (f.containsKey("userType") && !Set.of("PROVINCE","CITY","ENTERPRISE")
                .contains(f.get("userType"))) throw field("userType", "用户类型不合法");
        if (f.containsKey("dataStatus") && !Set.of("NO_FILING","DRAFT","PENDING",
                "RETURNED","APPROVED").contains(f.get("dataStatus")))
            throw field("dataStatus", "备案状态不合法");
        if (f.containsKey("reportStatus") && !Set.of("DRAFT","SUBMITTED_CITY",
                "SUBMITTED_PROVINCE","RETURNED_CITY","RETURNED_PROVINCE",
                "APPROVED_PROVINCE").contains(f.get("reportStatus")))
            throw field("reportStatus", "报表状态不合法");
        if (f.containsKey("reportStatus") && !f.containsKey("periodCode"))
            throw field("periodCode", "筛选报表状态时必填期次");
        Map<String, List<String>> regionSets = new LinkedHashMap<>();
        for (String key : List.of("cityCode","countyCode","regionCode")) if (f.containsKey(key)) {
            String code = f.get(key);
            java.util.List<com.example.employment.backend.week2.shared.dao.RegionScopeDao.Region> path;
            try { path = regions.path(code); }
            catch (java.util.NoSuchElementException ex) { throw field(key, "地区不存在"); }
            if (path.stream().noneMatch(r -> r.code().equals(province.regionCode())))
                throw field(key, "地区不属于本省");
            int level = path.getLast().level();
            if ("cityCode".equals(key) && level != 2) throw field(key, "应为市级代码");
            if ("countyCode".equals(key) && level != 3) throw field(key, "应为县区代码");
            regionSets.put(key, regions.descendants(code));
        }
        if (regionSets.size() > 1) {
            List<String> intersection = new ArrayList<>(regionSets.values().iterator().next());
            regionSets.values().forEach(intersection::retainAll);
            if (intersection.isEmpty()) throw field("regionCode", "地区条件相互矛盾");
        }
        LocalDate from = date(f, "createdFrom"), to = date(f, "createdTo");
        if (from != null && to != null && from.isAfter(to)) throw field("createdFrom", "开始日期晚于结束日期");
        if (from != null) f.put("createdFrom", from.atStartOfDay(SHANGHAI)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime().format(SQL_TIME));
        if (to != null) f.put("createdTo", to.plusDays(1).atStartOfDay(SHANGHAI)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime().format(SQL_TIME));
        YearMonth month = null;
        if (f.containsKey("month")) try { month = YearMonth.parse(f.get("month")); }
        catch (DateTimeException ex) { throw field("month", "格式应为YYYY-MM"); }
        String quarter = f.get("quarter");
        if (quarter != null && !quarter.matches("[0-9]{4}-Q[1-4]"))
            throw field("quarter", "格式应为YYYY-Q1至YYYY-Q4");
        if (f.containsKey("periodCode")) {
            SurveyPeriod p = periods.byCode(f.get("periodCode"), false)
                    .orElseThrow(() -> field("periodCode", "调查期不存在"));
            if (p.provinceOrgId() == null || p.provinceOrgId() != province.id())
                throw field("periodCode", "调查期不属于本省");
        }
        List<String> matchedPeriods = null;
        if (f.containsKey("periodCode") || month != null || quarter != null) {
            final YearMonth targetMonth = month;
            matchedPeriods = periods.visible(province.id()).stream()
                    .filter(p -> p.provinceOrgId() != null && p.provinceOrgId() == province.id())
                    .filter(p -> !f.containsKey("periodCode") || p.code().equals(f.get("periodCode")))
                    .filter(p -> targetMonth == null || YearMonth.from(p.startAt().atZone(SHANGHAI)).equals(targetMonth))
                    .filter(p -> quarter == null || (p.startAt().atZone(SHANGHAI).getYear() + "-Q"
                            + ((p.startAt().atZone(SHANGHAI).getMonthValue()-1)/3+1)).equals(quarter))
                    .map(SurveyPeriod::code).toList();
        }
        List<Map<String, Object>> rows = matchedPeriods != null && matchedPeriods.isEmpty()
                ? List.of() : accounts.rows(province.id(), f, regionSets, matchedPeriods);
        return new Result(rows.stream().map(r -> account(r, f)).toList(), input);
    }
    public Map<String, Object> page(Result result, int page, int size) {
        if (page < 0 || (long)page * size > Integer.MAX_VALUE) throw field("page", "页码不合法");
        if (size < 1 || size > 100) throw field("size", "每页应为1至100条");
        int from = Math.min(page * size, result.items().size());
        int to = Math.min(from + size, result.items().size());
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("items", result.items().subList(from, to));
        value.put("total", (long)result.items().size()); value.put("page", page);
        value.put("size", size); value.put("filters", result.filters());
        return value;
    }
    private static Map<String, Object> account(Map<String, Object> r, Map<String, String> f) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String[] pair : new String[][] {{"userId","user_id"}, {"unitName","unit_name"},
                {"username","username"}, {"userType","user_type"}, {"enabled","enabled"},
                {"provinceCode","province_code"}, {"provinceName","province_name"},
                {"cityCode","city_code"}, {"cityName","city_name"},
                {"countyCode","county_code"}, {"countyName","county_name"},
                {"regionCode","region_code"}, {"regionName","region_name"},
                {"natureCode","nature_code"}, {"natureName","nature_name"},
                {"industryCode","industry_code"}, {"industryName","industry_name"}})
            m.put(pair[0], "userId".equals(pair[0]) ? r.get(pair[1]).toString() : r.get(pair[1]));
        m.put("dataStatus", "ENTERPRISE".equals(r.get("user_type")) ? r.get("filing_status") : null);
        m.put("reportStatus", f.containsKey("periodCode") ? r.get("report_status") : null);
        Object created = r.get("created_at");
        LocalDateTime date = created instanceof java.sql.Timestamp timestamp
                ? timestamp.toLocalDateTime() : (LocalDateTime)created;
        m.put("createdAt", date.toInstant(ZoneOffset.UTC));
        return m;
    }
    private static LocalDate date(Map<String, String> f, String key) {
        if (!f.containsKey(key)) return null;
        try { return LocalDate.parse(f.get(key)); }
        catch (DateTimeException ex) { throw field(key, "格式应为YYYY-MM-DD"); }
    }
    private static FieldErrorException field(String key, String message) {
        return new FieldErrorException(Map.of(key, message));
    }
    public record Result(List<Map<String, Object>> items, Map<String, String> filters) { }
}
