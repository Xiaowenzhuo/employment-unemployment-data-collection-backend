package com.example.employment.backend.week3.period.service;

import com.example.employment.backend.common.ConflictException;
import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.common.NotFoundException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.filing.repository.FilingRepository;
import com.example.employment.backend.week2.filing.service.FilingService;
import com.example.employment.backend.week2.shared.dao.RegionScopeDao.Org;
import com.example.employment.backend.week2.shared.repository.RegionScopeRepository;
import com.example.employment.backend.week2.shared.service.ScopeService;
import com.example.employment.backend.week3.period.model.PeriodViews.*;
import com.example.employment.backend.week3.period.model.SurveyPeriod;
import com.example.employment.backend.week3.period.repository.PeriodRepository;
import com.example.employment.backend.week3.report.repository.ReportRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PeriodService {
    private final PeriodRepository periods;
    private final ReportRepository reports;
    private final FilingService filingService;
    private final FilingRepository filings;
    private final ScopeService scopes;
    private final RegionScopeRepository regions;

    public PeriodService(PeriodRepository periods, ReportRepository reports, FilingService filingService,
            FilingRepository filings, ScopeService scopes, RegionScopeRepository regions) {
        this.periods = periods;
        this.reports = reports;
        this.filingService = filingService;
        this.filings = filings;
        this.scopes = scopes;
        this.regions = regions;
    }

    public ProvinceList provinceList(UserAccount actor) {
        List<PeriodData> items = periods.visible(scopes.province(actor).id()).stream()
                .map(period -> view(period, Instant.now())).toList();
        return new ProvinceList(items, items.size());
    }

    @Transactional
    public PeriodData create(UserAccount actor, PeriodInput input) {
        Org province = scopes.province(actor);
        validate(input, true);
        SurveyPeriod period = new SurveyPeriod(input.code(), input.label().trim(),
                input.startAt(), input.endExclusiveAt(), input.openAt(), input.closeAt(),
                input.demo(), province.id(), 1);
        try {
            periods.insert(period);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("调查期代码已存在");
        }
        filings.audit(actor.id(), province.id(), "PERIOD_CREATED", period.code(), now());
        return view(period, Instant.now());
    }

    @Transactional
    public PeriodData update(UserAccount actor, String code, PeriodInput input) {
        Org province = scopes.province(actor);
        SurveyPeriod old = periods.byCode(code, true).orElseThrow(NotFoundException::new);
        if (old.provinceOrgId() != null && old.provinceOrgId() != province.id()) {
            throw new NotFoundException();
        }
        if (old.legacy()) throw new ConflictException("历史演示期次不可修改");
        validate(input, false);
        if (input.expectedVersion() == null || input.expectedVersion() < 1) {
            throw field("expectedVersion", "版本号必须为正整数");
        }
        if (old.currentVersion() != input.expectedVersion()) {
            throw new ConflictException("调查期版本已变化，请刷新后重试");
        }
        if (!Instant.now().isBefore(old.openAt()) || periods.hasReports(code)) {
            throw new ConflictException("调查期已开放或已有报表，不可修改");
        }
        SurveyPeriod updated = new SurveyPeriod(code, input.label().trim(), input.startAt(),
                input.endExclusiveAt(), input.openAt(), input.closeAt(), old.demo(),
                province.id(), old.currentVersion() + 1);
        if (periods.update(updated, old.currentVersion()) != 1) {
            throw new ConflictException("调查期版本已变化，请刷新后重试");
        }
        filings.audit(actor.id(), province.id(), "PERIOD_UPDATED", code, now());
        return view(updated, Instant.now());
    }

    public EnterpriseList enterpriseList(UserAccount actor) {
        Org enterprise = scopes.enterprise(actor);
        boolean eligible = filingService.canReport(enterprise.id());
        List<EnterprisePeriod> items = periods.visible(provinceId(enterprise)).stream()
                .map(period -> {
                    String status = reports.byOrgPeriod(enterprise.id(), period.code(), false)
                            .map(report -> report.status()).orElse(null);
                    String blocked = blockedReason(period, eligible, status, Instant.now());
                    PeriodData base = view(period, Instant.now());
                    return new EnterprisePeriod(base.code(), base.label(), base.startAt(),
                            base.endExclusiveAt(), base.openAt(), base.closeAt(), base.demo(),
                            base.legacy(), base.currentVersion(), base.windowStatus(), eligible,
                            blocked == null, blocked);
                }).toList();
        return new EnterpriseList(items);
    }

    public SurveyPeriod visibleToEnterprise(UserAccount actor, String code, boolean lock) {
        Org enterprise = scopes.enterprise(actor);
        SurveyPeriod period = periods.byCode(code, lock).orElseThrow(NotFoundException::new);
        if (period.provinceOrgId() != null && period.provinceOrgId() != provinceId(enterprise)) {
            throw new NotFoundException();
        }
        return period;
    }

    public static String blockedReason(SurveyPeriod period, boolean eligible, String reportStatus,
            Instant now) {
        if (period.legacy()) return "LEGACY_PERIOD";
        if ("SUBMITTED_CITY".equals(reportStatus)) return "ALREADY_SUBMITTED";
        if (!eligible) return "FILING_NOT_APPROVED";
        if (now.isBefore(period.openAt())) return "WINDOW_NOT_OPEN";
        if (!now.isBefore(period.closeAt())) return "WINDOW_CLOSED";
        return null;
    }

    private long provinceId(Org enterprise) {
        if (enterprise.parentId() == null) throw new NotFoundException();
        Org city = regions.org(enterprise.parentId()).orElseThrow(NotFoundException::new);
        if (!"CITY".equals(city.type()) || city.parentId() == null) throw new NotFoundException();
        Org province = regions.org(city.parentId()).orElseThrow(NotFoundException::new);
        if (!"PROVINCE".equals(province.type())) throw new NotFoundException();
        return province.id();
    }

    private void validate(PeriodInput input, boolean create) {
        if (create && (input.code() == null || !input.code().matches("[A-Z0-9_]{1,32}"))) {
            throw field("code", "应为1至32位大写字母、数字或下划线");
        }
        if (input.label() == null || input.label().trim().isEmpty()
                || input.label().trim().length() > 80) throw field("label", "应为1至80字");
        if (input.startAt() == null) throw field("startAt", "必填且应为UTC时间");
        if (input.endExclusiveAt() == null || !input.startAt().isBefore(input.endExclusiveAt())) {
            throw field("endExclusiveAt", "必须晚于调查期开始时间");
        }
        if (input.openAt() == null) throw field("openAt", "必填且应为UTC时间");
        if (input.closeAt() == null || !input.openAt().isBefore(input.closeAt())) {
            throw field("closeAt", "必须晚于开放时间");
        }
        if (create && input.demo() == null) throw field("demo", "必填");
    }

    private static PeriodData view(SurveyPeriod period, Instant now) {
        return new PeriodData(period.code(), period.label(), period.startAt(),
                period.endExclusiveAt(), period.openAt(), period.closeAt(), period.demo(),
                period.legacy(), period.currentVersion(), period.windowStatus(now));
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
    }

    private static FieldErrorException field(String name, String message) {
        return new FieldErrorException(Map.of(name, message));
    }
}
