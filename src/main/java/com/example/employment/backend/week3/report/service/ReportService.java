package com.example.employment.backend.week3.report.service;

import com.example.employment.backend.common.ConflictException;
import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.common.ForbiddenException;
import com.example.employment.backend.common.NotFoundException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.filing.repository.FilingRepository;
import com.example.employment.backend.week2.filing.service.FilingService;
import com.example.employment.backend.week2.shared.service.ScopeService;
import com.example.employment.backend.week3.period.model.SurveyPeriod;
import com.example.employment.backend.week3.period.service.PeriodService;
import com.example.employment.backend.week3.report.dao.ReportDao.OptionRow;
import com.example.employment.backend.week3.report.model.Report;
import com.example.employment.backend.week3.report.model.ReportForm;
import com.example.employment.backend.week3.report.model.ReportViews.*;
import com.example.employment.backend.week3.report.repository.ReportRepository;
import com.example.employment.backend.week4.review.repository.ReviewRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    private final ReportRepository reports;
    private final PeriodService periods;
    private final FilingService filingService;
    private final FilingRepository filings;
    private final ScopeService scopes;
    private final ReviewRepository reviews;

    public ReportService(ReportRepository reports, PeriodService periods, FilingService filingService,
            FilingRepository filings, ScopeService scopes, ReviewRepository reviews) {
        this.reports = reports;
        this.periods = periods;
        this.filingService = filingService;
        this.filings = filings;
        this.scopes = scopes;
        this.reviews = reviews;
    }

    public Options options(UserAccount actor) {
        scopes.enterprise(actor);
        List<OptionRow> rows = reports.options();
        List<Option> types = rows.stream().filter(r -> "TYPE".equals(r.category()))
                .map(r -> new Option(r.code(), r.name())).toList();
        List<Reason> reasons = rows.stream().filter(r -> "REASON".equals(r.category()))
                .filter(r -> r.parentCode() == null)
                .map(r -> new Reason(r.code(), r.name(), r.parentTypeCode(), null,
                        rows.stream().filter(c -> r.code().equals(c.parentCode()))
                                .map(c -> new ReasonChild(c.code(), c.name(), r.code())).toList()))
                .toList();
        return new Options(types, reasons);
    }

    public ReportList list(UserAccount actor, String periodCode, String status, int page, int size) {
        long orgId = scopes.enterprise(actor).id();
        if (page < 0 || (long) page * size > Integer.MAX_VALUE) throw field("page", "页码不合法");
        if (size < 1 || size > 100) throw field("size", "每页应为1至100条");
        if (status != null && !List.of("DRAFT", "SUBMITTED_CITY", "SUBMITTED_PROVINCE",
                "RETURNED_CITY", "RETURNED_PROVINCE", "APPROVED_PROVINCE").contains(status)) {
            throw field("status", "状态不合法");
        }
        if (periodCode != null) {
            try { periods.visibleToEnterprise(actor, periodCode, false); }
            catch (NotFoundException exception) { throw field("periodCode", "调查期不存在"); }
        }
        return new ReportList(reports.list(orgId, periodCode, status, size, page * size).stream()
                .map(item -> new ListItem(item.reportId(), item.periodCode(), item.periodLabel(),
                        item.status(), item.currentVersion(), item.submissionVersion(),
                        item.submittedAt(), latestReturn(item.reportId(), item.status()))).toList(),
                reports.count(orgId, periodCode, status), page, size);
    }

    public ReportView detail(UserAccount actor, String periodCode) {
        SurveyPeriod period = periods.visibleToEnterprise(actor, periodCode, false);
        long orgId = actor.orgId();
        Report report = reports.byOrgPeriod(orgId, periodCode, false).orElse(null);
        return view(period, report, filingService.canReport(orgId), reports.baseline(orgId).orElse(null));
    }

    @Transactional
    public ReportView save(UserAccount actor, String periodCode, Integer expectedVersion, ReportForm input) {
        SurveyPeriod period = periods.visibleToEnterprise(actor, periodCode, true);
        long orgId = actor.orgId();
        reports.lockOrg(orgId);
        Report existing = reports.byOrgPeriod(orgId, periodCode, true).orElse(null);
        checkWrite(period, existing, filingService.canReport(orgId));
        if (expectedVersion == null || expectedVersion < 0) {
            throw field("expectedVersion", "版本号必须为非负整数");
        }
        int current = existing == null ? 0 : existing.currentVersion();
        if (current != expectedVersion) throw new ConflictException("报表版本已变化，请刷新后重试");
        Integer baseline = reports.baseline(orgId).orElse(null);
        ReportForm form = normalize(input);
        validate(form, baseline, false);
        LocalDateTime now = now();
        if (existing == null) {
            reports.insert(reports.nextId(), orgId, periodCode, form, now);
        } else if (reports.updateDraft(existing.id(), current, form, now) != 1) {
            throw new ConflictException("报表版本已变化，请刷新后重试");
        }
        Report saved = reports.byOrgPeriod(orgId, periodCode, false).orElseThrow();
        filings.audit(actor.id(), orgId, "REPORT_DRAFT_SAVED", periodCode, now);
        return view(period, saved, true, baseline);
    }

    @Transactional
    public SubmitResult submit(UserAccount actor, String periodCode, Integer expectedVersion) {
        SurveyPeriod period = periods.visibleToEnterprise(actor, periodCode, true);
        long orgId = actor.orgId();
        reports.lockOrg(orgId);
        Report existing = reports.byOrgPeriod(orgId, periodCode, true).orElse(null);
        checkWrite(period, existing, filingService.canReport(orgId));
        if (expectedVersion == null || expectedVersion < 1) {
            throw field("expectedVersion", "版本号必须为正整数");
        }
        if (existing == null) throw new ConflictException("请先保存草稿");
        if (existing.currentVersion() != expectedVersion) {
            throw new ConflictException("报表版本已变化，请刷新后重试");
        }
        Integer baseline = reports.baseline(orgId).orElse(null);
        validate(existing.form(), baseline, true);
        LocalDateTime now = now();
        if (baseline == null) {
            reports.insertBaseline(orgId, existing.form().baselineEmploymentCount(), existing.id());
        }
        if (reports.submit(existing.id(), expectedVersion, now) != 1) {
            throw new ConflictException("报表版本已变化，请刷新后重试");
        }
        int nextSubmission = existing.submissionVersion() + 1;
        var filing = filings.byOrg(orgId, false).orElseThrow(ForbiddenException::new);
        if (!"APPROVED".equals(filing.status())) throw new ForbiddenException();
        reports.snapshot(existing, nextSubmission, actor.id(), now,
                filing.id(), filing.currentVersion());
        filings.audit(actor.id(), orgId, "REPORT_SUBMITTED", periodCode, now);
        return new SubmitResult(Long.toString(existing.id()), periodCode, "SUBMITTED_CITY",
                expectedVersion + 1, nextSubmission, now.toInstant(ZoneOffset.UTC));
    }

    public History history(UserAccount actor, String periodCode) {
        periods.visibleToEnterprise(actor, periodCode, false);
        Report report = reports.byOrgPeriod(actor.orgId(), periodCode, false)
                .orElseThrow(NotFoundException::new);
        return new History(Long.toString(report.id()), periodCode, report.status(),
                reports.snapshots(report.id()), reviews.events(report.id()));
    }

    private void checkWrite(SurveyPeriod period, Report report, boolean eligible) {
        String blocked = PeriodService.blockedReason(period, eligible,
                report == null ? null : report.status(), Instant.now());
        if ("FILING_NOT_APPROVED".equals(blocked)) throw new ForbiddenException();
        if (blocked != null) throw new ConflictException(switch (blocked) {
            case "LEGACY_PERIOD" -> "历史演示期次不可填报";
            case "ALREADY_SUBMITTED" -> "报表已提交，不可修改";
            case "WINDOW_NOT_OPEN" -> "填报窗口尚未开放";
            default -> "填报窗口已关闭";
        });
    }

    private void validate(ReportForm form, Integer lockedBaseline, boolean submitting) {
        if (lockedBaseline != null && !lockedBaseline.equals(form.baselineEmploymentCount())) {
            throw field("baselineEmploymentCount", "必须沿用首次提交锁定的建档期人数");
        }
        count(form.baselineEmploymentCount(), "baselineEmploymentCount", submitting);
        count(form.periodEmploymentCount(), "periodEmploymentCount", submitting);
        length(form.otherReason(), "otherReason", submitting);
        length(form.primaryReasonDetail(), "primaryReasonDetail", false);
        length(form.secondaryReasonDetail(), "secondaryReasonDetail", false);
        length(form.tertiaryReasonDetail(), "tertiaryReasonDetail", false);
        boolean reduced = form.baselineEmploymentCount() != null && form.periodEmploymentCount() != null
                && form.periodEmploymentCount() < form.baselineEmploymentCount();
        if (!reduced && form.baselineEmploymentCount() != null && form.periodEmploymentCount() != null
                && (form.reductionTypeCode() != null || form.primaryReasonCode() != null
                || form.primaryReasonDetail() != null || form.secondaryReasonCode() != null
                || form.secondaryReasonDetail() != null || form.tertiaryReasonCode() != null
                || form.tertiaryReasonDetail() != null)) {
            throw field("reductionTypeCode", "人数未减少时，减少原因字段必须为空");
        }
        if (submitting && reduced) {
            if (form.reductionTypeCode() == null) throw field("reductionTypeCode", "人数减少时必填");
            if (form.primaryReasonCode() == null) throw field("primaryReasonCode", "人数减少时必填");
            if (form.primaryReasonDetail() == null) throw field("primaryReasonDetail", "人数减少时必填");
        }
        if ((form.secondaryReasonCode() == null) != (form.secondaryReasonDetail() == null)) {
            throw field("secondaryReasonCode", "次要原因代码与说明须同时填写");
        }
        if ((form.tertiaryReasonCode() == null) != (form.tertiaryReasonDetail() == null)) {
            throw field("tertiaryReasonCode", "第三原因代码与说明须同时填写");
        }
        List<OptionRow> options = reports.options();
        if (form.reductionTypeCode() != null && options.stream().noneMatch(o ->
                "TYPE".equals(o.category()) && o.code().equals(form.reductionTypeCode()))) {
            throw field("reductionTypeCode", "减少类型不存在");
        }
        reason(options, form.primaryReasonCode(), form.reductionTypeCode(), "primaryReasonCode");
        reason(options, form.secondaryReasonCode(), form.reductionTypeCode(), "secondaryReasonCode");
        reason(options, form.tertiaryReasonCode(), form.reductionTypeCode(), "tertiaryReasonCode");
    }

    private static void reason(List<OptionRow> options, String code, String type, String field) {
        if (code == null) return;
        if (type == null || options.stream().noneMatch(o -> "REASON".equals(o.category())
                && o.code().equals(code) && type.equals(o.parentTypeCode()))) {
            throw field(field, "原因代码与减少类型不匹配");
        }
    }

    private static void count(Integer value, String field, boolean required) {
        if (value == null && required) throw field(field, "必填");
        if (value != null && value < 0) throw field(field, "不能为负数");
    }

    private static void length(String value, String field, boolean required) {
        if (value == null && required) throw field(field, "必填");
        if (value != null && value.length() > 500) throw field(field, "最多500字");
    }

    private static ReportForm normalize(ReportForm form) {
        return new ReportForm(form.baselineEmploymentCount(), form.periodEmploymentCount(),
                trim(form.otherReason()), trim(form.reductionTypeCode()),
                trim(form.primaryReasonCode()), trim(form.primaryReasonDetail()),
                trim(form.secondaryReasonCode()), trim(form.secondaryReasonDetail()),
                trim(form.tertiaryReasonCode()), trim(form.tertiaryReasonDetail()));
    }

    private static String trim(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private ReportView view(SurveyPeriod period, Report report,
            boolean eligible, Integer baseline) {
        String blocked = PeriodService.blockedReason(period, eligible,
                report == null ? null : report.status(), Instant.now());
        return new ReportView(report == null ? null : Long.toString(report.id()), period.code(),
                report == null ? null : report.status(), report == null ? 0 : report.currentVersion(),
                report == null ? 0 : report.submissionVersion(), eligible,
                period.windowStatus(Instant.now()), blocked == null, blocked, baseline != null,
                baseline, report == null ? null : report.form(),
                report == null ? null : report.updatedAt(),
                report == null ? null : report.submittedAt(),
                report == null ? null : latestReturn(Long.toString(report.id()), report.status()));
    }

    private com.example.employment.backend.week4.review.model.ReviewViews.LatestReturn latestReturn(
            String reportId, String status) {
        if (!"RETURNED_CITY".equals(status) && !"RETURNED_PROVINCE".equals(status)) return null;
        return reviews.latestReturn(Long.parseLong(reportId)).orElse(null);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
    }

    private static FieldErrorException field(String name, String message) {
        return new FieldErrorException(Map.of(name, message));
    }
}
