package com.example.employment.backend.revision.service;

import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.shared.service.ScopeService;
import com.example.employment.backend.week3.period.repository.PeriodRepository;
import com.example.employment.backend.revision.dao.ProgressDao.Row;
import com.example.employment.backend.revision.repository.ProgressRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ProgressService {
    private final ProgressRepository progress;
    private final PeriodRepository periods;
    private final ScopeService scopes;
    public ProgressService(ProgressRepository progress,PeriodRepository periods,ScopeService scopes) {
        this.progress=progress;this.periods=periods;this.scopes=scopes;
    }
    public List<Row> all(UserAccount actor,String periodCode) {
        long provinceId=scopes.province(actor).id();
        if (periodCode==null || periodCode.isBlank()) throw field("periodCode","必填");
        var period=periods.byCode(periodCode,false)
                .orElseThrow(()->field("periodCode","调查期不存在"));
        if (period.provinceOrgId()==null || period.provinceOrgId()!=provinceId)
            throw field("periodCode","调查期不属于本省");
        return progress.rows(provinceId,periodCode);
    }
    public Map<String,Object> page(UserAccount actor,String periodCode,String cityCode,
            String filingStatus,String reportStatus,int page,int size) {
        List<Row> all=all(actor,periodCode);
        if (page<0 || (long)page*size>Integer.MAX_VALUE) throw field("page","页码不合法");
        if (size<1 || size>100) throw field("size","每页应为1至100条");
        if (filingStatus!=null && !List.of("NO_FILING","DRAFT","PENDING","RETURNED","APPROVED")
                .contains(filingStatus)) throw field("filingStatus","状态不合法");
        if (reportStatus!=null && !List.of("NOT_SUBMITTED","DRAFT","PENDING_CITY",
                "PENDING_PROVINCE","APPROVED","RETURNED","VOIDED").contains(reportStatus))
            throw field("reportStatus","状态不合法");
        Map<String,Long> filingCounts=new LinkedHashMap<>(),reportCounts=new LinkedHashMap<>();
        for (Row row:all) {
            filingCounts.merge(row.filingStatus(),1L,Long::sum);
            reportCounts.merge(row.effectiveReportStatus(),1L,Long::sum);
        }
        List<Row> filtered=all.stream().filter(row -> cityCode==null || cityCode.equals(row.cityCode()))
                .filter(row -> filingStatus==null || filingStatus.equals(row.filingStatus()))
                .filter(row -> reportStatus==null || matches(reportStatus,row.effectiveReportStatus()))
                .toList();
        int start=Math.min(page*size,filtered.size()),end=Math.min(start+size,filtered.size());
        List<Map<String,Object>> items=filtered.subList(start,end).stream().map(this::view).toList();
        Map<String,Object> summary=new LinkedHashMap<>();
        summary.put("totalEnterprises",all.size());summary.put("filingCounts",filingCounts);
        summary.put("reportCounts",reportCounts);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("periodCode",periodCode);result.put("summary",summary);
        result.put("items",items);result.put("total",filtered.size());
        result.put("page",page);result.put("size",size);
        return result;
    }
    private Map<String,Object> view(Row row) {
        Map<String,Object> item=new LinkedHashMap<>();
        item.put("enterpriseOrgId",Long.toString(row.enterpriseId()));
        item.put("enterpriseName",row.enterpriseName());
        item.put("cityCode",row.cityCode());item.put("cityName",row.cityName());
        item.put("filingStatus",row.filingStatus());
        item.put("reportStatus",row.effectiveReportStatus());
        item.put("reportId",row.reportId()==null?null:Long.toString(row.reportId()));
        item.put("reportVoided",row.voided());item.put("lastSubmittedAt",row.submittedAt());
        return item;
    }
    private static boolean matches(String filter,String raw) {
        return switch (filter) {
            case "PENDING_CITY" -> "SUBMITTED_CITY".equals(raw);
            case "PENDING_PROVINCE" -> "SUBMITTED_PROVINCE".equals(raw);
            case "APPROVED" -> "APPROVED_PROVINCE".equals(raw);
            case "RETURNED" -> raw.startsWith("RETURNED_");
            default -> filter.equals(raw);
        };
    }
    private static FieldErrorException field(String key,String message) {
        return new FieldErrorException(Map.of(key,message));
    }
}
