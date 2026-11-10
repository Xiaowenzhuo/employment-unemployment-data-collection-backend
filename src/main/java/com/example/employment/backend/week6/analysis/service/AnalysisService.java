package com.example.employment.backend.week6.analysis.service;

import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.shared.service.ScopeService;
import com.example.employment.backend.week3.period.model.SurveyPeriod;
import com.example.employment.backend.week3.period.repository.PeriodRepository;
import com.example.employment.backend.midterm.warehouse.service.WarehouseDataService;
import com.example.employment.backend.week5.data.service.ProvinceDataService.DataSet;
import com.example.employment.backend.week6.analysis.dao.AnalysisDao.Option;
import com.example.employment.backend.week6.analysis.repository.AnalysisRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AnalysisService {
    public static final String SAMPLE_RULE =
            "APPROVED_PROVINCE_NOT_VOIDED_LATEST_SUBMISSION_EFFECTIVE_VALUES";
    private final WarehouseDataService data;
    private final PeriodRepository periods;
    private final ScopeService scopes;
    private final AnalysisRepository options;
    public AnalysisService(WarehouseDataService data, PeriodRepository periods,
            ScopeService scopes, AnalysisRepository options) {
        this.data = data; this.periods = periods; this.scopes = scopes; this.options = options;
    }

    public CitiesAnalysis cities(UserAccount actor, String periodCode, String regionCode) {
        DataSet set = data.load(actor, periodCode, regionCode);
        Map<String, CityCount> counts = new HashMap<>();
        for (Map<String, Object> row : set.rows()) {
            String id = row.get("city_org_id").toString();
            CityCount city = counts.computeIfAbsent(id, key -> new CityCount(key,
                    (String)row.get("city_name"), (String)row.get("city_region_code")));
            city.count++;
        }
        long total = set.rows().size();
        List<CityShare> cities = counts.values().stream()
                .sorted(Comparator.comparing((CityCount c) -> c.regionCode)
                        .thenComparingLong(c -> Long.parseLong(c.id)))
                .map(c -> new CityShare(c.id, c.name, c.regionCode, c.count,
                        percent(c.count, total))).toList();
        return new CitiesAnalysis(set.period().code(), set.period().label(), regionCode,
                SAMPLE_RULE, total, total == 0, cities);
    }

    public CompareAnalysis compare(UserAccount actor, String first, String second,
            Filters filters) {
        if (first == null || first.isBlank()) throw field("firstPeriodCode", "必填");
        if (second == null || second.isBlank()) throw field("secondPeriodCode", "必填");
        if (first.equals(second)) throw field("secondPeriodCode", "两个调查期必须不同");
        validateFilters(filters);
        DataSet a = data.load(actor, first, filters.regionCode());
        DataSet b = data.load(actor, second, filters.regionCode());
        return new CompareAnalysis(first, second, filters, SAMPLE_RULE,
                List.of(new ComparePeriod("FIRST", a.period().code(), a.period().label(),
                                a.period().startAt(), metrics(filtered(a, filters))),
                        new ComparePeriod("SECOND", b.period().code(), b.period().label(),
                                b.period().startAt(), metrics(filtered(b, filters)))));
    }

    public TrendAnalysis trend(UserAccount actor, String rawCodes, Filters filters) {
        long provinceId = scopes.province(actor).id();
        validateFilters(filters);
        if (rawCodes == null || rawCodes.isBlank()) throw field("periodCodes", "必填");
        List<String> requested = List.of(rawCodes.split(",", -1));
        if (requested.size() < 2 || requested.size() > 24
                || requested.stream().anyMatch(code -> code.isBlank() || !code.equals(code.trim()))
                || Set.copyOf(requested).size() != requested.size())
            throw field("periodCodes", "须传2至24个不同且有序的期次代码");
        List<SurveyPeriod> ordered = periods.visible(provinceId).stream()
                .filter(p -> p.provinceOrgId() != null && p.provinceOrgId() == provinceId)
                .sorted(Comparator.comparing(SurveyPeriod::startAt).thenComparing(SurveyPeriod::code))
                .toList();
        List<String> codes = ordered.stream().map(SurveyPeriod::code).toList();
        int start = codes.indexOf(requested.getFirst());
        if (start < 0 || start + requested.size() > codes.size()
                || !codes.subList(start, start + requested.size()).equals(requested))
            throw field("periodCodes", "须选本省按时间相邻的真实调查期，且顺序正确");
        List<TrendPoint> points = new ArrayList<>();
        for (String code : requested) {
            DataSet set = data.load(actor, code, filters.regionCode());
            Metrics m = metrics(filtered(set, filters));
            points.add(new TrendPoint(code, set.period().label(), set.period().startAt(),
                    m.enterpriseCount() > 0, m.enterpriseCount() > 0 ? m : null));
        }
        return new TrendAnalysis(requested, filters, SAMPLE_RULE, points);
    }

    public BreakdownResult breakdown(UserAccount actor,String periodCode,String groupBy,Filters filters) {
        if (groupBy==null || !Set.of("CITY","NATURE","INDUSTRY","CHANGE_DIRECTION")
                .contains(groupBy))
            throw field("groupBy","分组类型不合法");
        validateFilters(filters);
        DataSet set=data.load(actor,periodCode,filters.regionCode());
        Map<String,List<Map<String,Object>>> groups=new LinkedHashMap<>();
        Map<String,String> names=new HashMap<>();
        for (Map<String,Object> row:filtered(set,filters)) {
            String code=groupCode(groupBy,row);
            names.putIfAbsent(code,groupName(groupBy,row,code));
            groups.computeIfAbsent(code,key->new ArrayList<>()).add(row);
        }
        List<BreakdownRow> items=groups.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry->{
                    List<Map<String,Object>> rows=entry.getValue();
                    Metrics m=metrics(rows);
                    long increase=0,up=0,flat=0,down=0;
                    for (Map<String,Object> row:rows) {
                        long delta=delta(row);
                        increase+=Math.max(delta,0);
                        if (delta>0) up++;else if (delta<0) down++;else flat++;
                    }
                    return new BreakdownRow(entry.getKey(),names.get(entry.getKey()),
                            m.enterpriseCount(),m.baselineEmploymentTotal(),
                            m.periodEmploymentTotal(),m.netChange(),m.reductionTotal(),
                            increase,m.changePercent(),down,flat,up);
                }).toList();
        return new BreakdownResult(periodCode,groupBy,filters,items);
    }

    public EnterpriseAnalysisResult enterprises(UserAccount actor,String periodCode,
            String changeDirection,Filters filters,int page,int size) {
        if (changeDirection!=null && !Set.of("INCREASE","UNCHANGED","DECREASE")
                .contains(changeDirection)) throw field("changeDirection","变动类型不合法");
        if (page<0 || (long)page*size>Integer.MAX_VALUE) throw field("page","页码不合法");
        if (size<1 || size>100) throw field("size","每页应为1至100条");
        validateFilters(filters);
        DataSet set=data.load(actor,periodCode,filters.regionCode());
        List<Map<String,Object>> rows=filtered(set,filters).stream()
                .filter(row->changeDirection==null || changeDirection.equals(direction(row)))
                .toList();
        int from=Math.min(page*size,rows.size()),to=Math.min(from+size,rows.size());
        List<Map<String,Object>> items=rows.subList(from,to).stream().map(row->{
            Map<String,Object> item=new LinkedHashMap<>();
            item.put("enterpriseOrgId",row.get("org_id").toString());
            item.put("enterpriseName",row.get("enterprise_name"));
            item.put("cityCode",row.get("city_region_code"));
            item.put("cityName",row.get("city_name"));
            item.put("natureCode",row.get("nature_code"));
            item.put("natureName",row.get("nature_name"));
            item.put("industryCode",row.get("industry_code"));
            item.put("industryName",row.get("industry_name"));
            item.put("reportId",row.get("report_id").toString());
            item.put("submissionVersion",row.get("submission_version"));
            item.put("correctionVersion",row.get("correction_count"));
            item.put("baselineSourcePeriodCode",row.get("baseline_source_period_code"));
            long baseline=number(row,"baseline_employment_count");
            long current=number(row,"period_employment_count");
            item.put("baselineEmploymentCount",baseline);
            item.put("periodEmploymentCount",current);
            item.put("netChange",current-baseline);
            item.put("changePercent",percent(current-baseline,baseline));
            item.put("changeDirection",direction(row));
            item.put("primaryReasonCode",row.get("primary_reason_code"));
            item.put("primaryReasonDetail",row.get("primary_reason_detail"));
            return item;
        }).toList();
        return new EnterpriseAnalysisResult(periodCode,items,rows.size(),page,size);
    }

    private static String groupCode(String groupBy,Map<String,Object> row) {
        return switch (groupBy) {
            case "CITY" -> string(row.get("city_region_code"));
            case "NATURE" -> string(row.get("nature_code"));
            case "INDUSTRY" -> string(row.get("industry_code"));
            default -> direction(row);
        };
    }
    private static String groupName(String groupBy,Map<String,Object> row,String code) {
        if ("CHANGE_DIRECTION".equals(groupBy)) return switch (code) {
            case "INCREASE" -> "增员";case "DECREASE" -> "减员";default -> "不变";
        };
        Object name=row.get(switch (groupBy) {
            case "CITY" -> "city_name";case "NATURE" -> "nature_name";
            default -> "industry_name";
        });
        return name==null?"未提供":name.toString();
    }
    private static String string(Object value) { return value==null?"UNAVAILABLE":value.toString(); }
    private static long number(Map<String,Object> row,String field) {
        return ((Number)row.get(field)).longValue();
    }
    private static long delta(Map<String,Object> row) {
        return number(row,"period_employment_count")-number(row,"baseline_employment_count");
    }
    private static String direction(Map<String,Object> row) {
        long value=delta(row);
        return value>0?"INCREASE":value<0?"DECREASE":"UNCHANGED";
    }

    private void validateFilters(Filters f) {
        validatePair("natureParentCode", f.natureParentCode(), "natureCode", f.natureCode(), "NATURE");
        validatePair("industryParentCode", f.industryParentCode(), "industryCode", f.industryCode(), "INDUSTRY");
        if (f.enterpriseOrgId()!=null && !f.enterpriseOrgId().matches("[1-9][0-9]*"))
            throw field("enterpriseOrgId","应为企业组织 ID");
    }
    private void validatePair(String parentField, String parent, String childField, String child,
            String category) {
        if (parent != null) {
            Option option = options.option(parent).orElseThrow(() -> field(parentField, "代码不存在"));
            if (!category.equals(option.category()) || option.parentCode() != null)
                throw field(parentField, "应为该类别的父级代码");
        }
        if (child != null) {
            Option option = options.option(child).orElseThrow(() -> field(childField, "代码不存在"));
            if (!category.equals(option.category()) || option.parentCode() == null)
                throw field(childField, "应为该类别的子级代码");
            if (parent != null && !parent.equals(option.parentCode()))
                throw field(childField, "与父级代码不匹配");
        }
    }
    private static List<Map<String, Object>> filtered(DataSet set, Filters f) {
        return set.rows().stream().filter(row ->
                matches(row, "nature_parent_code", f.natureParentCode())
                && matches(row, "nature_code", f.natureCode())
                && matches(row, "industry_parent_code", f.industryParentCode())
                && matches(row, "industry_code", f.industryCode())
                && matchesId(row, f.enterpriseOrgId())).toList();
    }
    private static boolean matchesId(Map<String,Object> row,String selected) {
        return selected==null || selected.equals(row.get("org_id").toString());
    }
    private static boolean matches(Map<String, Object> row, String key, String selected) {
        return selected == null || Objects.equals(row.get(key), selected);
    }
    private static Metrics metrics(List<Map<String, Object>> rows) {
        long baseline = 0, current = 0, reduction = 0;
        long frozen = 0, fallback = 0, unavailable = 0;
        for (Map<String, Object> row : rows) {
            long b = ((Number)row.get("baseline_employment_count")).longValue();
            long c = ((Number)row.get("period_employment_count")).longValue();
            baseline += b; current += c; reduction += Math.max(b - c, 0);
            if (row.get("snapshot_filing_id") != null) frozen++;
            else if (row.get("current_filing_id") != null) fallback++;
            else unavailable++;
        }
        long net = current - baseline;
        Map<String, Long> sources = new LinkedHashMap<>();
        sources.put("SNAPSHOT_VERSION", frozen);
        sources.put("CURRENT_FALLBACK", fallback);
        sources.put("UNAVAILABLE", unavailable);
        return new Metrics(rows.size(), baseline, current, net, reduction,
                percent(net, baseline), sources);
    }
    private static BigDecimal percent(long numerator, long denominator) {
        return denominator == 0 ? null : BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }
    private static FieldErrorException field(String key, String message) {
        return new FieldErrorException(Map.of(key, message));
    }
    private static final class CityCount {
        final String id, name, regionCode;
        long count;
        CityCount(String id, String name, String regionCode) {
            this.id = id; this.name = name; this.regionCode = regionCode;
        }
    }
    public record Filters(String regionCode, String natureParentCode, String natureCode,
            String industryParentCode, String industryCode, String enterpriseOrgId) { }
    public record Metrics(long enterpriseCount, long baselineEmploymentTotal,
            long periodEmploymentTotal, long netChange, long reductionTotal,
            BigDecimal changePercent, Map<String, Long> filingSourceCounts) { }
    public record CityShare(String cityOrgId, String cityName, String cityRegionCode,
            long enterpriseCount, BigDecimal sharePercent) { }
    public record CitiesAnalysis(String periodCode, String periodLabel, String regionCode,
            String sampleRule, long totalEnterpriseCount, boolean empty, List<CityShare> cities) { }
    public record ComparePeriod(String position, String periodCode, String periodLabel,
            Instant startAt, Metrics metrics) { }
    public record CompareAnalysis(String firstPeriodCode, String secondPeriodCode,
            Filters filters, String sampleRule, List<ComparePeriod> periods) { }
    public record TrendPoint(String periodCode, String periodLabel, Instant startAt,
            boolean hasSample, Metrics metrics) { }
    public record TrendAnalysis(List<String> periodCodes, Filters filters,
            String sampleRule, List<TrendPoint> points) { }
    public record BreakdownRow(String groupCode,String groupName,long enterpriseCount,
            long baselineEmploymentTotal,long periodEmploymentTotal,long netChange,
            long reductionTotal,long increaseTotal,BigDecimal changePercent,
            long decreaseEnterpriseCount,long unchangedEnterpriseCount,
            long increaseEnterpriseCount) { }
    public record BreakdownResult(String periodCode,String groupBy,Filters filters,
            List<BreakdownRow> items) { }
    public record EnterpriseAnalysisResult(String periodCode,List<Map<String,Object>> items,
            int total,int page,int size) { }
}
