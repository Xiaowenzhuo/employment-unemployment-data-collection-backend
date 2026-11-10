package com.example.employment.backend.midterm.year.service;

import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.midterm.year.repository.YearRepository;
import com.example.employment.backend.national.service.NationalApiException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.shared.service.ScopeService;
import com.example.employment.backend.week3.period.model.SurveyPeriod;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class YearService {
    private static final ZoneId BEIJING=ZoneId.of("Asia/Shanghai");
    private final YearRepository years;
    private final ScopeService scopes;
    public YearService(YearRepository years, ScopeService scopes) {
        this.years=years;this.scopes=scopes;
    }
    public Preview preview(UserAccount actor,Integer year) {
        validate(year);
        return inspect(scopes.province(actor).id(),year);
    }
    @Transactional
    public Result create(UserAccount actor,Integer year) {
        validate(year);
        long province=scopes.province(actor).id();
        years.lockProvince(province);
        Preview preview=inspect(province,year);
        if (!preview.canCreate()) throw new NationalApiException(HttpStatus.CONFLICT,
                "全年调查期存在冲突，本次未创建任何月份", "YEAR_CONFLICT",
                Map.of("conflictMonths",preview.months().stream()
                        .filter(m->"CONFLICT".equals(m.disposition())).toList()));
        List<Month> created=new ArrayList<>(),existing=new ArrayList<>();
        try {
            for (Month month:preview.months()) {
                if ("EXISTING".equals(month.disposition())) { existing.add(month);continue; }
                years.insert(new SurveyPeriod(month.periodCode(),month.periodLabel(),
                        month.startAt(),month.endExclusiveAt(),month.openAt(),month.closeAt(),
                        false,province,1));
                created.add(month);
            }
        } catch (DuplicateKeyException ex) {
            throw new NationalApiException(HttpStatus.CONFLICT,
                    "调查期代码已被占用，本次未创建任何月份", "YEAR_CONFLICT",Map.of());
        }
        return new Result(year,created,existing,12);
    }
    private Preview inspect(long province,Integer year) {
        List<SurveyPeriod> existing=years.provincePeriods(province);
        List<Month> months=new ArrayList<>();
        int create=0,reuse=0,conflict=0;
        for (int month=1;month<=12;month++) {
            LocalDate start=LocalDate.of(year,month,1),end=start.plusMonths(1);
            Instant startAt=start.atStartOfDay(BEIJING).toInstant();
            Instant endAt=end.atStartOfDay(BEIJING).toInstant();
            Instant closeAt=end.plusDays(7).atStartOfDay(BEIJING).toInstant();
            String code="P"+year+String.format("%02d",month);
            SurveyPeriod same=years.byCode(code).orElse(null);
            String reason=null, disposition="CREATE";
            boolean overlapsOther=existing.stream().anyMatch(p->!p.code().equals(code)
                    && !p.demo() && startAt.isBefore(p.endExclusiveAt())
                    && endAt.isAfter(p.startAt()));
            if (same!=null) {
                if (same.provinceOrgId()!=null && same.provinceOrgId()==province && !same.demo()
                        && startAt.equals(same.startAt()) && endAt.equals(same.endExclusiveAt())
                        && endAt.equals(same.openAt()) && closeAt.equals(same.closeAt())
                        && !overlapsOther) {
                    disposition="EXISTING";
                } else { disposition="CONFLICT";reason="CODE_BOUNDARY_MISMATCH"; }
            } else if (overlapsOther) {
                disposition="CONFLICT";reason="OVERLAPPING_PERIOD";
            }
            switch(disposition) { case "CREATE"->create++;case "EXISTING"->reuse++;default->conflict++; }
            months.add(new Month(code,String.format("%04d 年 %02d 月调查期",year,month),
                    startAt,endAt,endAt,closeAt,disposition,reason));
        }
        return new Preview(year,conflict==0,months,create,reuse,conflict);
    }
    private static void validate(Integer year) {
        if (year==null || year<2000 || year>2100)
            throw new FieldErrorException(Map.of("year","应为2000至2100的整数"));
    }
    public record Month(String periodCode,String periodLabel,Instant startAt,Instant endExclusiveAt,
            Instant openAt,Instant closeAt,String disposition,String conflictReason) { }
    public record Preview(int year,boolean canCreate,List<Month> months,int createdCount,
            int existingCount,int conflictCount) { }
    public record Result(int year,List<Month> created,List<Month> existing,int total) { }
}
