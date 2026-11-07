package com.example.employment.backend.revision.service;

import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.common.ForbiddenException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.filing.service.FilingService;
import com.example.employment.backend.week2.shared.dao.RegionScopeDao.Org;
import com.example.employment.backend.week2.shared.repository.RegionScopeRepository;
import com.example.employment.backend.week2.shared.service.ScopeService;
import com.example.employment.backend.week3.period.model.SurveyPeriod;
import com.example.employment.backend.week3.period.repository.PeriodRepository;
import com.example.employment.backend.week3.report.model.ReportViews.ReportView;
import com.example.employment.backend.week3.report.service.ReportService;
import com.example.employment.backend.week6.notice.service.NoticeService;
import com.example.employment.backend.revision.dao.ProgressDao.Row;
import com.example.employment.backend.revision.repository.ProgressRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OverviewService {
    private final ScopeService scopes;
    private final RegionScopeRepository regions;
    private final PeriodRepository periods;
    private final ProgressRepository progress;
    private final FilingService filings;
    private final ReportService reports;
    private final NoticeService notices;
    public OverviewService(ScopeService scopes,RegionScopeRepository regions,
            PeriodRepository periods,ProgressRepository progress,FilingService filings,
            ReportService reports,NoticeService notices) {
        this.scopes=scopes;this.regions=regions;this.periods=periods;this.progress=progress;
        this.filings=filings;this.reports=reports;this.notices=notices;
    }

    public Map<String,Object> enterprise(UserAccount actor,String code) {
        Org enterprise=scopes.enterprise(actor);
        long provinceId=provinceOf(enterprise);
        SurveyPeriod selected=select(provinceId,code);
        var filing=filings.status(actor);
        String filingStatus=filing.status()==null?"NO_FILING":filing.status();
        ReportView report=selected==null?null:reports.detail(actor,selected.code());
        String reportStatus=report==null?null:report.status();
        String reason=report!=null && report.latestReturn()!=null
                ? report.latestReturn().comment():filing.returnReason();
        String step;
        Map<String,Object> action;
        if (!"APPROVED".equals(filingStatus)) {
            step="WAIT_FOR_FILING";
            action=action("完善备案","/enterprise/filing",true);
        } else if (selected==null) {
            step="WAIT_FOR_PERIOD";
            action=action("查看调查期","/enterprise/periods",false);
        } else if (reportStatus!=null && reportStatus.startsWith("RETURNED_")) {
            step="FIX_RETURNED_REPORT";
            action=action("修改并重新提交","/enterprise/reports/"+selected.code(),true);
        } else if (reportStatus==null || "DRAFT".equals(reportStatus)) {
            step="COMPLETE_REPORT";
            action=action("填写并提交","/enterprise/reports/"+selected.code(),
                    report==null || report.canWrite());
        } else {
            step="WAIT_FOR_REVIEW";
            action=action("查看报表","/enterprise/reports/"+selected.code(),true);
        }
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("selectedPeriod",periodView(selected));out.put("filingStatus",filingStatus);
        out.put("reportStatus",reportStatus);out.put("latestReturnReason",reason);
        out.put("currentStep",step);out.put("nextAction",action);
        out.put("visibleNoticeCount",notices.list(actor,0,1).total());
        return out;
    }

    public Map<String,Object> city(UserAccount actor,String code) {
        Org city=own(actor,"CITY");
        long provinceId=provinceOf(city);
        SurveyPeriod selected=select(provinceId,code);
        List<Row> rows=selected==null?List.of():progress.rows(provinceId,selected.code())
                .stream().filter(r->r.cityCode().equals(city.regionCode())).toList();
        Map<String,Object> counts=new LinkedHashMap<>();
        counts.put("pendingCity",count(rows,"SUBMITTED_CITY"));
        counts.put("approvedCity",rows.stream().filter(r->List.of("SUBMITTED_PROVINCE",
                "RETURNED_PROVINCE","APPROVED_PROVINCE").contains(r.effectiveReportStatus())).count());
        counts.put("returnedCity",count(rows,"RETURNED_CITY"));
        counts.put("forwardedProvince",rows.stream().filter(r->List.of("SUBMITTED_PROVINCE",
                "RETURNED_PROVINCE","APPROVED_PROVINCE").contains(r.effectiveReportStatus())).count());
        counts.put("notSubmitted",rows.stream().filter(r->List.of("NOT_SUBMITTED","DRAFT")
                .contains(r.effectiveReportStatus())).count());
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("selectedPeriod",periodView(selected));out.put("reportCounts",counts);
        out.put("noticeEntry",Map.of("label","通知管理","route","/city/notices"));
        out.put("nextAction",action("审核待办",selected==null?"/city/reports":
                "/city/reports?periodCode="+selected.code(),selected!=null));
        return out;
    }

    public Map<String,Object> province(UserAccount actor,String code) {
        long provinceId=scopes.province(actor).id();
        SurveyPeriod selected=select(provinceId,code);
        List<Row> rows=selected==null?List.of():progress.rows(provinceId,selected.code());
        Map<String,long[]> cities=new LinkedHashMap<>();
        Map<String,String> names=new LinkedHashMap<>();
        for (var city:progress.cities(provinceId)) {
            cities.put(city.code(),new long[3]);
            names.put(city.code(),city.name());
        }
        for (Row row:rows) {
            names.put(row.cityCode(),row.cityName());
            long[] values=cities.computeIfAbsent(row.cityCode(),key->new long[3]);
            if ("APPROVED".equals(row.filingStatus())) {
                values[0]++;
                if (List.of("SUBMITTED_PROVINCE","RETURNED_PROVINCE","APPROVED_PROVINCE")
                        .contains(row.effectiveReportStatus())) values[1]++;
                else values[2]++;
            }
        }
        List<Map<String,Object>> cityItems=new ArrayList<>();
        long sentCities=0;
        for (var entry:cities.entrySet()) {
            long[] values=entry.getValue();if (values[1]>0) sentCities++;
            cityItems.add(Map.of("cityCode",entry.getKey(),"cityName",names.get(entry.getKey()),
                    "eligibleEnterpriseCount",values[0],"submittedEnterpriseCount",values[1],
                    "notSubmittedEnterpriseCount",values[2]));
        }
        Map<String,Object> cityProgress=new LinkedHashMap<>();
        cityProgress.put("submittedCityCount",sentCities);
        cityProgress.put("notSubmittedCityCount",cities.size()-sentCities);
        cityProgress.put("items",cityItems);
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("selectedPeriod",periodView(selected));
        out.put("pendingFilingCount",rows.stream().filter(r->"PENDING".equals(r.filingStatus())).count());
        out.put("pendingReportCount",count(rows,"SUBMITTED_PROVINCE"));
        out.put("approvedReportCount",count(rows,"APPROVED_PROVINCE"));
        out.put("cityProgress",cityProgress);
        out.put("entries",List.of(Map.of("label","备案审核","route","/province/filings"),
                Map.of("label","报表审核","route","/province/reports"),
                Map.of("label","企业进度","route","/province/enterprise-progress")));
        return out;
    }

    private SurveyPeriod select(long provinceId,String code) {
        List<SurveyPeriod> visible=periods.visible(provinceId).stream()
                .filter(p->p.provinceOrgId()!=null && p.provinceOrgId()==provinceId)
                .sorted(Comparator.comparing(SurveyPeriod::startAt).thenComparing(SurveyPeriod::code))
                .toList();
        if (code!=null && !code.isBlank()) return visible.stream().filter(p->code.equals(p.code()))
                .findFirst().orElseThrow(()->field("periodCode","调查期不存在或不属于本省"));
        return visible.stream().filter(p->"OPEN".equals(p.windowStatus(Instant.now())))
                .reduce((a,b)->b).orElse(visible.isEmpty()?null:visible.getLast());
    }
    private Org own(UserAccount actor,String role) {
        Org org=regions.org(actor.orgId()).orElseThrow(ForbiddenException::new);
        if (!org.enabled() || !role.equals(org.type()) || !actor.roleCodes().contains(role))
            throw new ForbiddenException();
        return org;
    }
    private long provinceOf(Org org) {
        Org parent=regions.org(org.parentId()).orElseThrow(ForbiddenException::new);
        if ("PROVINCE".equals(parent.type()) && parent.enabled()) return parent.id();
        if (!"CITY".equals(parent.type()) || !parent.enabled()) throw new ForbiddenException();
        Org province=regions.org(parent.parentId()).orElseThrow(ForbiddenException::new);
        if (!"PROVINCE".equals(province.type()) || !province.enabled()) throw new ForbiddenException();
        return province.id();
    }
    private static long count(List<Row> rows,String status) {
        return rows.stream().filter(r->status.equals(r.effectiveReportStatus())).count();
    }
    private static Map<String,Object> action(String label,String route,boolean enabled) {
        return Map.of("label",label,"route",route,"enabled",enabled);
    }
    private static Map<String,Object> periodView(SurveyPeriod period) {
        if (period==null) return null;
        return Map.of("periodCode",period.code(),"periodLabel",period.label(),
                "deadlineAt",period.closeAt(),"windowStatus",period.windowStatus(Instant.now()));
    }
    private static FieldErrorException field(String key,String message) {
        return new FieldErrorException(Map.of(key,message));
    }
}
