package com.example.employment.backend.week5.correction.service;

import com.example.employment.backend.common.ConflictException;
import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.common.NotFoundException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.shared.service.ScopeService;
import com.example.employment.backend.week3.report.dao.ReportDao.OptionRow;
import com.example.employment.backend.week3.report.model.Report;
import com.example.employment.backend.week3.report.model.ReportForm;
import com.example.employment.backend.week3.report.model.ReportViews.Snapshot;
import com.example.employment.backend.week3.report.repository.ReportRepository;
import com.example.employment.backend.week5.correction.dao.CorrectionDao.CorrectionEntry;
import com.example.employment.backend.week5.correction.dao.CorrectionDao.VoidEvent;
import com.example.employment.backend.week5.correction.repository.CorrectionRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CorrectionService {
    private static final Set<String> EDITABLE = Set.of("periodEmploymentCount", "otherReason",
            "reductionTypeCode", "primaryReasonCode", "primaryReasonDetail",
            "secondaryReasonCode", "secondaryReasonDetail", "tertiaryReasonCode",
            "tertiaryReasonDetail");
    private final CorrectionRepository corrections;
    private final ReportRepository reports;
    private final ScopeService scopes;
    public CorrectionService(CorrectionRepository corrections, ReportRepository reports,
            ScopeService scopes) {
        this.corrections = corrections;
        this.reports = reports;
        this.scopes = scopes;
    }

    public CorrectionDetail detail(UserAccount actor, String rawId) {
        Report report = scoped(actor, rawId, false);
        if (!"APPROVED_PROVINCE".equals(report.status())) throw new NotFoundException();
        Snapshot snapshot = snapshot(report);
        return new CorrectionDetail(rawId, report.periodCode(), report.status(),
                report.currentVersion(), report.submissionVersion(), corrections.voided(report.id()),
                corrections.voidedAt(report.id()), snapshot.form(), report.form(),
                corrections.corrections(report.id()), corrections.voidEvent(report.id()).orElse(null));
    }

    @Transactional
    public CorrectionResult correct(UserAccount actor, String rawId, Integer expected,
            Integer submission, String rawReason, Map<String, Object> changes) {
        String reason = reason(rawReason);
        versions(expected, submission);
        Report report = scoped(actor, rawId, true);
        current(report, expected, submission);
        if (changes.isEmpty()) throw field("changes", "至少修改一个字段");
        Map<String, Object> old = values(report.form());
        Map<String, Object> next = new LinkedHashMap<>(old);
        for (var change : changes.entrySet()) {
            String key = change.getKey();
            if (!EDITABLE.contains(key)) throw field(key, "不支持修订该字段");
            Object value = change.getValue();
            if ("periodEmploymentCount".equals(key)) {
                if (value != null && !(value instanceof Integer)) throw field(key, "应为整数");
            } else if (value != null && !(value instanceof String)) {
                throw field(key, "应为字符串");
            } else if (value instanceof String s) {
                value = s.trim().isEmpty() ? null : s.trim();
            }
            next.put(key, value);
        }
        List<String> changed = new ArrayList<>();
        for (String key : changes.keySet()) if (!Objects.equals(old.get(key), next.get(key))) changed.add(key);
        if (changed.isEmpty()) throw field("changes", "没有实际变化");
        ReportForm form = form(next);
        validate(form);
        LocalDateTime now = now();
        if (corrections.update(report.id(), expected, submission, form, now) != 1) throw conflict();
        long operation = corrections.nextCorrectionId();
        for (String key : changed) {
            long id = key.equals(changed.getFirst()) ? operation : corrections.nextCorrectionId();
            corrections.insertCorrection(id, operation, report.id(), submission, key,
                    old.get(key), next.get(key), reason, actor.id(), actor.displayName(), now);
        }
        return new CorrectionResult(rawId, report.status(), expected + 1, submission,
                Long.toString(operation), changed, form);
    }

    @Transactional
    public VoidResult voidReport(UserAccount actor, String rawId, Integer expected,
            Integer submission, String rawReason) {
        String reason = reason(rawReason);
        versions(expected, submission);
        Report report = scoped(actor, rawId, true);
        current(report, expected, submission);
        LocalDateTime now = now();
        if (corrections.voidReport(report.id(), expected, submission, now) != 1) throw conflict();
        long id = corrections.nextVoidId();
        corrections.insertVoid(id, report.id(), submission, reason,
                actor.id(), actor.displayName(), now);
        VoidEvent event = new VoidEvent(Long.toString(id), reason, Long.toString(actor.id()),
                actor.displayName(), now.toInstant(ZoneOffset.UTC), submission);
        return new VoidResult(rawId, report.status(), expected + 1, submission, true, event);
    }

    private Report scoped(UserAccount actor, String rawId, boolean lock) {
        var province = scopes.province(actor);
        long id;
        try { id = Long.parseLong(rawId); if (id < 1) throw new NumberFormatException(); }
        catch (NumberFormatException ex) { throw field("reportId", "应为正整数"); }
        Report report = reports.byId(id, lock).orElseThrow(NotFoundException::new);
        scopes.visibleFiling(report.orgId(), province);
        return report;
    }
    private Snapshot snapshot(Report report) {
        return reports.snapshots(report.id()).stream()
                .filter(s -> s.submissionVersion() == report.submissionVersion())
                .findFirst().orElseThrow(IllegalStateException::new);
    }
    private void current(Report report, int expected, int submission) {
        if (!"APPROVED_PROVINCE".equals(report.status()) || corrections.voided(report.id())
                || report.currentVersion() != expected || report.submissionVersion() != submission)
            throw conflict();
        snapshot(report);
    }
    private static void versions(Integer expected, Integer submission) {
        if (expected == null || expected < 1) throw field("expectedVersion", "应为正整数");
        if (submission == null || submission < 1) throw field("submissionVersion", "应为正整数");
    }
    private static String reason(String value) {
        if (value == null || value.trim().isEmpty() || value.trim().length() > 500)
            throw field("reason", "须填写1至500字理由");
        return value.trim();
    }
    private void validate(ReportForm f) {
        if (f.periodEmploymentCount() == null || f.periodEmploymentCount() < 0)
            throw field("periodEmploymentCount", "应为非负整数");
        if (f.otherReason() == null || f.otherReason().length() > 500)
            throw field("otherReason", "必填，最多500字");
        for (String name : List.of("primaryReasonDetail", "secondaryReasonDetail", "tertiaryReasonDetail")) {
            String value = (String) values(f).get(name);
            if (value != null && value.length() > 500) throw field(name, "最多500字");
        }
        boolean reduced = f.periodEmploymentCount() < f.baselineEmploymentCount();
        if (!reduced && (f.reductionTypeCode() != null || f.primaryReasonCode() != null
                || f.primaryReasonDetail() != null || f.secondaryReasonCode() != null
                || f.secondaryReasonDetail() != null || f.tertiaryReasonCode() != null
                || f.tertiaryReasonDetail() != null))
            throw field("reductionTypeCode", "人数未减少时原因字段须为空");
        if (reduced && (f.reductionTypeCode() == null || f.primaryReasonCode() == null
                || f.primaryReasonDetail() == null))
            throw field("primaryReasonCode", "人数减少时须填写类型、主要原因和说明");
        if ((f.secondaryReasonCode() == null) != (f.secondaryReasonDetail() == null))
            throw field("secondaryReasonCode", "代码与说明须同时填写");
        if ((f.tertiaryReasonCode() == null) != (f.tertiaryReasonDetail() == null))
            throw field("tertiaryReasonCode", "代码与说明须同时填写");
        List<OptionRow> options = reports.options();
        if (f.reductionTypeCode() != null && options.stream().noneMatch(o ->
                "TYPE".equals(o.category()) && o.code().equals(f.reductionTypeCode())))
            throw field("reductionTypeCode", "减少类型不存在");
        checkReason(options, f.primaryReasonCode(), f.reductionTypeCode(), "primaryReasonCode");
        checkReason(options, f.secondaryReasonCode(), f.reductionTypeCode(), "secondaryReasonCode");
        checkReason(options, f.tertiaryReasonCode(), f.reductionTypeCode(), "tertiaryReasonCode");
    }
    private static void checkReason(List<OptionRow> options, String code, String type, String field) {
        if (code != null && options.stream().noneMatch(o -> "REASON".equals(o.category())
                && o.code().equals(code) && Objects.equals(type, o.parentTypeCode())))
            throw field(field, "原因代码与减少类型不匹配");
    }
    private static Map<String, Object> values(ReportForm f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("baselineEmploymentCount", f.baselineEmploymentCount());
        m.put("periodEmploymentCount", f.periodEmploymentCount());
        m.put("otherReason", f.otherReason());
        m.put("reductionTypeCode", f.reductionTypeCode());
        m.put("primaryReasonCode", f.primaryReasonCode());
        m.put("primaryReasonDetail", f.primaryReasonDetail());
        m.put("secondaryReasonCode", f.secondaryReasonCode());
        m.put("secondaryReasonDetail", f.secondaryReasonDetail());
        m.put("tertiaryReasonCode", f.tertiaryReasonCode());
        m.put("tertiaryReasonDetail", f.tertiaryReasonDetail());
        return m;
    }
    private static ReportForm form(Map<String, Object> m) {
        return new ReportForm((Integer)m.get("baselineEmploymentCount"),
                (Integer)m.get("periodEmploymentCount"), (String)m.get("otherReason"),
                (String)m.get("reductionTypeCode"), (String)m.get("primaryReasonCode"),
                (String)m.get("primaryReasonDetail"), (String)m.get("secondaryReasonCode"),
                (String)m.get("secondaryReasonDetail"), (String)m.get("tertiaryReasonCode"),
                (String)m.get("tertiaryReasonDetail"));
    }
    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
    }
    private static ConflictException conflict() { return new ConflictException("报表状态或版本已变化，请刷新后重试"); }
    private static FieldErrorException field(String key, String message) {
        return new FieldErrorException(Map.of(key, message));
    }
    public record CorrectionDetail(String reportId, String periodCode, String status,
            int currentVersion, int submissionVersion, boolean voided, Instant voidedAt,
            ReportForm originalForm, ReportForm effectiveForm, List<CorrectionEntry> corrections,
            VoidEvent voidEvent) { }
    public record CorrectionResult(String reportId, String status, int currentVersion,
            int submissionVersion, String operationId, List<String> changedFields,
            ReportForm effectiveForm) { }
    public record VoidResult(String reportId, String status, int currentVersion,
            int submissionVersion, boolean voided, VoidEvent voidEvent) { }
}
