package com.example.employment.backend.national.service;

import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.common.ForbiddenException;
import com.example.employment.backend.common.NotFoundException;
import com.example.employment.backend.national.dao.NationalDao.BatchRow;
import com.example.employment.backend.national.dao.NationalDao.SourceRow;
import com.example.employment.backend.national.repository.NationalRepository;
import com.example.employment.backend.national.service.NationalViews.*;
import com.example.employment.backend.midterm.national.service.NationalV2Service;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.shared.service.ScopeService;
import com.example.employment.backend.week3.period.model.SurveyPeriod;
import com.example.employment.backend.week3.period.repository.PeriodRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class NationalService {
    private static final Pattern PERIOD = Pattern.compile("[A-Z0-9_]{1,32}");
    private static final Set<String> STATUSES = Set.of("QUEUED", "SENDING", "ASSEMBLING", "MOCK_ACCEPTED", "SEND_FAILED");
    private final NationalRepository batches;
    private final PeriodRepository periods;
    private final ScopeService scopes;
    private final JsonMapper json;
    private final NationalSender sender;
    private final NationalV2Service v2;

    public NationalService(NationalRepository batches, PeriodRepository periods,
            ScopeService scopes, JsonMapper json, NationalSender sender, NationalV2Service v2) {
        this.batches = batches;
        this.periods = periods;
        this.scopes = scopes;
        this.json = json;
        this.sender = sender;
        this.v2 = v2;
    }

    public Readiness readiness(UserAccount actor, String code) {
        long province = province(actor);
        return inspect(findPeriod(province, code, false), province, Instant.now());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public QueueResult create(UserAccount actor, String code, boolean acknowledgeMissing) {
        long province = province(actor);
        SurveyPeriod period = findPeriod(province, code, true);
        batches.lockReports(code);
        Readiness readiness = inspect(period, province, Instant.now());
        if (readiness.existingBatch() != null) {
            throw error(HttpStatus.CONFLICT, "本期已有上报批次", "BATCH_EXISTS",
                    Map.of("existingBatchId", readiness.existingBatch().batchId(), "blockers", readiness.blockers()));
        }
        if (!readiness.blockers().isEmpty()) {
            throw error(HttpStatus.CONFLICT, "本期尚不满足上报条件", "NOT_READY",
                    Map.of("blockers", readiness.blockers()));
        }
        if (readiness.requiresMissingAcknowledgement() && !acknowledgeMissing) {
            throw error(HttpStatus.CONFLICT, "请确认已知晓本期缺报企业", "MISSING_ACKNOWLEDGEMENT",
                    Map.of("blockers", List.of()));
        }
        String id = "YN-" + code + "-0001";
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Preview preview = readiness.preview();
        Payload payload = new Payload("demo-1", id, "MOCK", "53", code,
                period.startAt(), period.endExclusiveAt(), now,
                preview.summary(), preview.cities(), preview.enterprises());
        byte[] bytes;
        try { bytes = json.writeValueAsBytes(payload); }
        catch (JacksonException e) { throw invalid("报文序列化失败"); }
        String hash = sha256(bytes);
        Summary s = preview.summary();
        batches.insert(new BatchRow(id, province, code, "QUEUED",
                new String(bytes, StandardCharsets.UTF_8), hash, s.enterpriseCount(),
                s.baselineEmploymentTotal(), s.periodEmploymentTotal(), s.netChange(),
                now, null, null, null, null));
        for (Enterprise e : preview.enterprises()) {
            batches.insertItem(id, Long.parseLong(e.reportId()), e.submissionVersion(),
                    e.currentVersion(), e.baselineEmploymentCount(), e.periodEmploymentCount());
        }
        dispatchAfterCommit(id);
        return queued(id, s.enterpriseCount());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public QueueResult retry(UserAccount actor, String id) {
        long province = province(actor);
        BatchRow batch = batches.byId(province, id).orElseThrow(NotFoundException::new);
        if (!"QUEUED".equals(batch.status()) && !"SEND_FAILED".equals(batch.status())) {
            throw error(HttpStatus.CONFLICT, "当前批次不能继续发送", "RETRY_NOT_ALLOWED", Map.of());
        }
        if ("SEND_FAILED".equals(batch.status()) && batches.queueRetry(id) != 1)
            throw error(HttpStatus.CONFLICT, "批次状态已变化，请刷新后重试",
                    "RETRY_NOT_ALLOWED", Map.of());
        dispatchAfterCommit(id);
        return queued(id, batch.enterpriseCount());
    }

    public BatchList list(UserAccount actor, String period, String status, int page, int size) {
        long province = province(actor);
        if (period != null) validatePeriod(period);
        if (status != null && !STATUSES.contains(status)) throw field("status", "批次状态不合法");
        if (page < 0) throw field("page", "应为非负整数");
        if (size < 1 || size > 100) throw field("size", "应为1至100");
        return new BatchList(batches.list(province, period, status, page, size)
                .stream().map(this::summary).toList(),
                batches.count(province, period, status), page, size);
    }

    public BatchDetail detail(UserAccount actor, String id) {
        long province = province(actor);
        BatchRow b = batches.byId(province, id).orElseThrow(NotFoundException::new);
        Payload payload;
        try { payload = json.readValue(b.payloadJson(), Payload.class); }
        catch (JacksonException e) { throw new IllegalStateException("冻结报文无法读取", e); }
        List<Attempt> attempts = batches.attempts(id).stream()
                .map(a -> new Attempt(a.attemptNo(), a.startedAt(), a.finishedAt(),
                        a.result(), a.errorSummary(), a.mockReceiptId())).toList();
        Receipt receipt = b.receiptId() == null ? null : new Receipt(id, b.receiptId(),
                "ACCEPTED", b.receiptAt(), b.enterpriseCount(), b.payloadSha256());
        BatchSummary s = summary(b);
        return new BatchDetail(s.batchId(), s.periodCode(), s.mode(), s.status(),
                s.enterpriseCount(), s.payloadSha256(), s.createdAt(), s.lastSentAt(),
                s.retryCount(), s.lastError(), payload, attempts, receipt);
    }

    public Card card(UserAccount actor, String code) {
        province(actor);
        if (code == null) return new Card(null, "NO_PERIOD", "暂无调查期",
                "/province/national", null, false, false);
        if (!"530000".equals(scopes.province(actor).regionCode()))
            return new Card(code, "BLOCKED", "本期暂不可上报",
                    "/province/national", null, false, false);
        Map<String,Object> readiness=v2.readiness(actor,code);
        @SuppressWarnings("unchecked")
        Map<String,Object> existing=(Map<String,Object>)readiness.get("existingBatch");
        String state=existing==null ? (Boolean.TRUE.equals(readiness.get("canSubmit")) ? "READY" : "BLOCKED")
                : existing.get("status").toString();
        String label = switch (state) {
            case "READY" -> "本期可上报";
            case "QUEUED", "SENDING", "ASSEMBLING" -> "模拟发送中";
            case "MOCK_ACCEPTED" -> "模拟已接收";
            case "SEND_FAILED" -> "发送失败";
            default -> "本期暂不可上报";
        };
        return new Card(code, state, label, "/province/national",
                existing == null ? null : existing.get("batchId").toString(), "READY".equals(state),
                existing!=null && "XML".equals(existing.get("payloadFormat")) &&
                ("QUEUED".equals(state) || "SEND_FAILED".equals(state)));
    }

    private Readiness inspect(SurveyPeriod period, long province, Instant now) {
        List<SourceRow> rows = batches.source(province, period.code());
        List<Missing> missing = new ArrayList<>();
        List<Enterprise> enterprises = new ArrayList<>();
        long pendingCity = 0, pendingProvince = 0, returnedCity = 0, returnedProvince = 0, voided = 0;
        for (SourceRow r : rows) {
            if (r.voided()) { voided++; continue; }
            String status = r.reportStatus();
            if ("SUBMITTED_CITY".equals(status)) pendingCity++;
            else if ("SUBMITTED_PROVINCE".equals(status)) pendingProvince++;
            else if ("RETURNED_CITY".equals(status)) returnedCity++;
            else if ("RETURNED_PROVINCE".equals(status)) returnedProvince++;
            else if ("APPROVED_PROVINCE".equals(status)) enterprises.add(enterprise(r));
            else if ((status == null || "DRAFT".equals(status)) && "APPROVED".equals(r.filingStatus())) {
                missing.add(new Missing(Long.toString(r.orgId()),
                        nonblank(r.currentEnterpriseName(), r.orgName()),
                        cityCode(r.cityCode()), r.regionCode(), status));
            }
        }
        enterprises.sort(Comparator.comparing(Enterprise::regionCode)
                .thenComparing(Enterprise::organizationCode));
        Preview preview = preview(enterprises, rows);
        Counts counts = new Counts(enterprises.size(), pendingCity, pendingProvince,
                returnedCity, returnedProvince, voided, missing.size());
        ExistingBatch existing = batches.byPeriod(province, period.code())
                .map(b -> new ExistingBatch(b.batchId(), b.status())).orElse(null);
        List<Blocker> blockers = new ArrayList<>();
        if (period.legacy()) blockers.add(new Blocker("LEGACY_PERIOD", "旧演示调查期不可上报"));
        if (!period.legacy() && now.isBefore(period.closeAt()))
            blockers.add(new Blocker("WINDOW_NOT_CLOSED", "填报窗口尚未关闭"));
        if (pendingCity > 0) blockers.add(new Blocker("PENDING_CITY", "仍有市级待审报表"));
        if (pendingProvince > 0) blockers.add(new Blocker("PENDING_PROVINCE", "仍有省级待审报表"));
        if (returnedCity > 0) blockers.add(new Blocker("RETURNED_CITY", "仍有市级退回待重报报表"));
        if (returnedProvince > 0) blockers.add(new Blocker("RETURNED_PROVINCE", "仍有省级退回待重报报表"));
        if (enterprises.isEmpty()) blockers.add(new Blocker("NO_VALID_REPORT", "没有有效省审通过报表"));
        if (existing != null) blockers.add(new Blocker("BATCH_EXISTS", "本期已有上报批次"));
        Period view = new Period(period.code(), period.label(), period.startAt(),
                period.endExclusiveAt(), period.closeAt(), period.windowStatus(now), period.legacy());
        return new Readiness(view, blockers.isEmpty(), blockers, !missing.isEmpty(),
                counts, missing, preview, existing);
    }

    private Enterprise enterprise(SourceRow r) {
        String organizationCode = nonblank(r.versionOrgCode(), r.currentOrgCode());
        String name = nonblank(r.versionEnterpriseName(), r.currentEnterpriseName());
        String nature = nonblank(r.versionNatureCode(), r.currentNatureCode());
        String industry = nonblank(r.versionIndustryCode(), r.currentIndustryCode());
        if (organizationCode == null || name == null || nature == null || industry == null ||
                r.reportId() == null || r.submissionVersion() == null || r.currentVersion() == null ||
                r.baselineCount() == null || r.periodCount() == null || r.otherReason() == null ||
                r.baselineSourcePeriodCode() == null || r.baselineCount() < 0 || r.periodCount() < 0 ||
                r.submissionVersion() < 1 || r.currentVersion() < 1 ||
                r.regionCode() == null || !r.regionCode().matches("53[0-9]{4}") ||
                !r.regionCode().startsWith(cityCode(r.cityCode())))
            throw invalid("报表或备案资料缺少上报必填字段");
        boolean reduced = r.periodCount() < r.baselineCount();
        if (reduced && (r.reductionTypeCode() == null || r.primaryReasonCode() == null ||
                r.primaryReasonDetail() == null) ||
                !reduced && (r.reductionTypeCode() != null || r.primaryReasonCode() != null ||
                        r.primaryReasonDetail() != null))
            throw invalid("减少人数与主要原因字段不一致");
        return new Enterprise(organizationCode, name, cityCode(r.cityCode()), r.regionCode(),
                nature, industry, Long.toString(r.reportId()), r.submissionVersion(),
                r.currentVersion(), r.baselineSourcePeriodCode(), r.baselineCount(),
                r.periodCount(), r.periodCount() - r.baselineCount(), r.reductionTypeCode(),
                r.primaryReasonCode(), r.primaryReasonDetail(), r.otherReason());
    }

    private Preview preview(List<Enterprise> enterprises, List<SourceRow> rows) {
        Map<String, String> names = new LinkedHashMap<>();
        for (SourceRow row : rows) names.put(cityCode(row.cityCode()), row.cityName());
        Map<String, List<Enterprise>> groups = new LinkedHashMap<>();
        for (Enterprise e : enterprises) groups.computeIfAbsent(e.cityCode(), k -> new ArrayList<>()).add(e);
        List<City> cities = new ArrayList<>();
        long baseline = 0, current = 0;
        for (var group : groups.entrySet()) {
            long cityBaseline = group.getValue().stream().mapToLong(Enterprise::baselineEmploymentCount).sum();
            long cityCurrent = group.getValue().stream().mapToLong(Enterprise::periodEmploymentCount).sum();
            baseline += cityBaseline; current += cityCurrent;
            cities.add(new City(group.getKey(), names.get(group.getKey()), group.getValue().size(),
                    cityBaseline, cityCurrent, cityCurrent - cityBaseline));
        }
        cities.sort(Comparator.comparing(City::cityCode));
        if (baseline != enterprises.stream().mapToLong(Enterprise::baselineEmploymentCount).sum() ||
                current != enterprises.stream().mapToLong(Enterprise::periodEmploymentCount).sum())
            throw invalid("上报合计与企业明细不一致");
        return new Preview(new Summary(enterprises.size(), baseline, current, current - baseline),
                cities, enterprises);
    }

    private SurveyPeriod findPeriod(long province, String code, boolean lock) {
        validatePeriod(code);
        SurveyPeriod period = periods.byCode(code, lock).orElseThrow(NotFoundException::new);
        if (period.provinceOrgId() != null && period.provinceOrgId() != province)
            throw new NotFoundException();
        return period;
    }
    private long province(UserAccount actor) {
        if (!actor.roleCodes().contains("PROVINCE")) throw new ForbiddenException();
        return scopes.province(actor).id();
    }
    private static void validatePeriod(String code) {
        if (code == null || !PERIOD.matcher(code).matches()) throw field("periodCode", "期次代码不合法");
    }
    private static String cityCode(String full) {
        if (full == null || !full.matches("53[0-9]{4}")) throw invalid("州市地区代码不合法");
        return full.substring(0, 4);
    }
    private static String nonblank(String first, String fallback) {
        return first != null && !first.isBlank() ? first :
                fallback != null && !fallback.isBlank() ? fallback : null;
    }
    private static QueueResult queued(String id, int count) {
        return new QueueResult(id, "QUEUED", "MOCK", count,
                "/province/national/batches/" + id);
    }
    private BatchSummary summary(BatchRow b) {
        return new BatchSummary(b.batchId(), b.periodCode(), "MOCK", b.status(),
                b.enterpriseCount(), b.payloadSha256(), b.createdAt(), b.lastSentAt(),
                Math.max(0, batches.attemptCount(b.batchId()) - 1), b.lastError());
    }
    private void dispatchAfterCommit(String id) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { sender.send(id); }
        });
    }
    private static String sha256(byte[] bytes) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static NationalApiException invalid(String message) {
        return error(HttpStatus.BAD_REQUEST, message, "PAYLOAD_INVALID", Map.of());
    }
    private static NationalApiException error(HttpStatus status, String message,
            String reason, Map<String, Object> details) {
        return new NationalApiException(status, message, reason, details);
    }
    private static FieldErrorException field(String key, String message) {
        return new FieldErrorException(Map.of(key, message));
    }
}
