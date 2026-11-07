package com.example.employment.backend.week3.report.service;

import com.example.employment.backend.common.ConflictException;
import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.common.ForbiddenException;
import com.example.employment.backend.common.NotFoundException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.shared.service.ScopeService;
import com.example.employment.backend.week3.period.repository.PeriodRepository;
import com.example.employment.backend.week3.report.model.Report;
import com.example.employment.backend.week3.report.model.ReportViews.SubmitResult;
import com.example.employment.backend.week3.report.repository.ReportRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class BatchSubmitService {
    private final ReportService reports;
    private final ReportRepository repository;
    private final PeriodRepository periods;
    private final ScopeService scopes;

    public BatchSubmitService(ReportService reports,ReportRepository repository,
            PeriodRepository periods,ScopeService scopes) {
        this.reports=reports;this.repository=repository;this.periods=periods;this.scopes=scopes;
    }

    public BatchResult submit(UserAccount actor,List<BatchRequest> requested) {
        long orgId=scopes.enterprise(actor).id();
        if (requested==null || requested.isEmpty())
            throw new FieldErrorException(Map.of("items","至少选择一份报表"));
        Set<String> unique=new HashSet<>();
        for (BatchRequest item:requested) {
            if (item.periodCode()==null || !item.periodCode().matches("[A-Z0-9_]{1,32}"))
                throw new FieldErrorException(Map.of("periodCode","期次代码不合法"));
            if (item.expectedVersion()==null || item.expectedVersion()<0)
                throw new FieldErrorException(Map.of("expectedVersion","应为非负整数"));
            if (!unique.add(item.periodCode()))
                throw new FieldErrorException(Map.of("items","同一期次不可重复选择"));
        }
        List<BatchRequest> ordered=requested.stream().sorted(Comparator
                .comparing((BatchRequest item) -> periods.byCode(item.periodCode(),false)
                        .map(period -> period.startAt()).orElse(Instant.MAX))
                .thenComparing(BatchRequest::periodCode)).toList();
        List<BatchItem> items=new ArrayList<>();
        int successes=0;
        for (BatchRequest item:ordered) {
            try {
                SubmitResult result=reports.submit(actor,item.periodCode(),item.expectedVersion());
                items.add(new BatchItem(item.periodCode(),result.reportId(),true,null,null,
                        result.currentVersion(),result.submissionVersion(),
                        result.baselineSourcePeriodCode(),result.baselineEmploymentCount()));
                successes++;
            } catch (FieldErrorException | ConflictException | ForbiddenException
                    | NotFoundException exception) {
                Report current=repository.byOrgPeriod(orgId,item.periodCode(),false).orElse(null);
                items.add(new BatchItem(item.periodCode(),current==null?null:Long.toString(current.id()),
                        false,reasonCode(exception),reason(exception),
                        current==null?null:current.currentVersion(),
                        current==null?null:current.submissionVersion(),null,null));
            }
        }
        return new BatchResult(successes,items.size()-successes,items);
    }

    private static String reasonCode(RuntimeException exception) {
        if (exception instanceof NotFoundException) return "PERIOD_NOT_FOUND";
        if (exception instanceof ForbiddenException) return "FILING_NOT_APPROVED";
        if (exception instanceof FieldErrorException field) {
            return field.fieldErrors().containsKey("expectedVersion") ? "VERSION_CONFLICT" : "FORM_INVALID";
        }
        String message=exception.getMessage();
        if (message.contains("尚未开放")) return "WINDOW_NOT_OPEN";
        if (message.contains("关闭")) return "WINDOW_CLOSED";
        if (message.contains("版本")) return "VERSION_CONFLICT";
        return "REPORT_NOT_DRAFT";
    }
    private static String reason(RuntimeException exception) {
        if (exception instanceof FieldErrorException field) return field.fieldErrors().toString();
        if (exception instanceof NotFoundException) return "调查期不存在或不可访问";
        if (exception instanceof ForbiddenException) return "当前备案未通过";
        return exception.getMessage();
    }

    public record BatchRequest(String periodCode,Integer expectedVersion) { }
    public record BatchItem(String periodCode,String reportId,boolean success,String reasonCode,
            String reason,Integer currentVersion,Integer submissionVersion,
            String baselineSourcePeriodCode,Integer baselineEmploymentCount) { }
    public record BatchResult(int successCount,int failureCount,List<BatchItem> items) { }
}
