package com.example.employment.backend.week2.filing.service;

import com.example.employment.backend.common.ConflictException;
import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.common.NotFoundException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.filing.dao.FilingDao.ListRow;
import com.example.employment.backend.week2.filing.dao.FilingDao.OptionRow;
import com.example.employment.backend.week2.filing.model.Filing;
import com.example.employment.backend.week2.filing.model.FilingVersion;
import com.example.employment.backend.week2.filing.model.FilingViews.*;
import com.example.employment.backend.week2.filing.model.Profile;
import com.example.employment.backend.week2.filing.repository.FilingRepository;
import com.example.employment.backend.week2.shared.dao.RegionScopeDao.Org;
import com.example.employment.backend.week2.shared.repository.RegionScopeRepository;
import com.example.employment.backend.week2.shared.service.ScopeService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FilingService {
    private final FilingRepository filings;
    private final RegionScopeRepository regions;
    private final ScopeService scopes;

    public FilingService(FilingRepository filings, RegionScopeRepository regions, ScopeService scopes) {
        this.filings = filings;
        this.regions = regions;
        this.scopes = scopes;
    }

    public ProfileData profile(UserAccount actor) {
        Org org = scopes.enterprise(actor);
        Filing filing = filings.byOrg(org.id(), false).orElse(null);
        return filing == null
                ? new ProfileData(null, null, 0, true, regions.info(org.regionCode()), null, null)
                : new ProfileData(id(filing.id()), filing.status(), filing.currentVersion(),
                        editable(filing.status()), regions.info(org.regionCode()),
                        filing.profile(), filing.updatedAt());
    }

    @Transactional
    public ProfileData save(UserAccount actor, Profile input) {
        Org org = scopes.enterprise(actor);
        Filing existing = filings.byOrg(org.id(), true).orElse(null);
        if (existing != null && !editable(existing.status())) {
            throw new ConflictException("当前备案状态不可修改资料");
        }
        Profile p = normalize(input);
        validate(p, false);
        if (p.organizationCode() != null &&
                filings.codeExists(p.organizationCode(), existing == null ? -1 : existing.id())) {
            throw new ConflictException("组织机构代码已被使用");
        }
        LocalDateTime now = now();
        try {
            if (existing == null) {
                filings.insertDraft(filings.nextId(), org.id(), p, now);
            } else {
                filings.updateDraft(existing.id(), p, now);
            }
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("组织机构代码已被使用");
        }
        return profile(actor);
    }

    public StatusData status(UserAccount actor) {
        Org org = scopes.enterprise(actor);
        Filing filing = filings.byOrg(org.id(), false).orElse(null);
        if (filing == null) {
            return new StatusData(null, null, 0, false, null, null, null, List.of());
        }
        List<History> history = history(filing.id());
        FilingVersion latest = filing.currentVersion() == 0 ? null :
                filings.version(filing.id(), filing.currentVersion()).orElseThrow();
        return new StatusData(id(filing.id()), filing.status(), filing.currentVersion(),
                canReport(org.id()), latest == null ? null : latest.submittedAt(),
                latest == null ? null : latest.reviewedAt(),
                "RETURNED".equals(filing.status()) && latest != null ? latest.reason() : null,
                history);
    }

    public boolean canReport(long orgId) {
        return filings.byOrg(orgId, false).map(f -> "APPROVED".equals(f.status())).orElse(false);
    }

    @Transactional
    public SubmitData submit(UserAccount actor, Integer expectedVersion) {
        Org org = scopes.enterprise(actor);
        if (expectedVersion == null || expectedVersion < 0) {
            throw field("expectedVersion", "版本号必须为非负整数");
        }
        Filing filing = filings.byOrg(org.id(), true)
                .orElseThrow(() -> new ConflictException("请先保存企业资料"));
        if (!editable(filing.status()) || filing.currentVersion() != expectedVersion) {
            throw new ConflictException("备案状态或版本已变化，请刷新后重试");
        }
        validate(filing.profile(), true);
        LocalDateTime now = now();
        if (filings.setPending(filing.id(), filing.status(), filing.currentVersion(), now) != 1) {
            throw new ConflictException("备案状态或版本已变化，请刷新后重试");
        }
        int version = filing.currentVersion() + 1;
        filings.insertVersion(filing.id(), version, filing.profile(), actor.id(), now);
        filings.audit(actor.id(), org.id(), "FILING_SUBMITTED", Integer.toString(version), now);
        return new SubmitData(id(filing.id()), "PENDING", version,
                now.toInstant(ZoneOffset.UTC), false);
    }

    public VersionData ownVersion(UserAccount actor, int version) {
        Org org = scopes.enterprise(actor);
        validateVersion(version);
        Filing filing = filings.byOrg(org.id(), false).orElseThrow(NotFoundException::new);
        return version(filing.id(), version, org.regionCode());
    }

    public OptionsData options(UserAccount actor) {
        scopes.enterprise(actor);
        return new OptionsData(options("NATURE"), options("INDUSTRY"), options("ADDRESS"), true);
    }

    public List<Period> periods(UserAccount actor) {
        scopes.province(actor);
        return filings.periods();
    }

    public ListData list(UserAccount actor, String status, String regionCode,
            String periodCode, int page, int size) {
        Query query = query(actor, status, regionCode, periodCode);
        if (page < 0) {
            throw field("page", "页码不能小于0");
        }
        if (size < 1 || size > 100) {
            throw field("size", "每页应为1至100条");
        }
        if ((long) page * size > Integer.MAX_VALUE) {
            throw field("page", "页码过大");
        }
        long total = filings.count(query.provinceId(), query.regionCodes(),
                query.status(), query.endExclusive());
        List<ListItem> items = filings.list(query.provinceId(), query.regionCodes(),
                query.status(), query.endExclusive(), size, page * size).stream()
                .map(this::item).toList();
        return new ListData(items, total, page, size,
                new Filters(query.status(), query.regionCode(), query.periodCode()));
    }

    public List<ListRow> exportRows(UserAccount actor, String regionCode, String periodCode) {
        Query query = query(actor, "APPROVED", regionCode, periodCode);
        LocalDateTime now = now();
        filings.audit(actor.id(), query.provinceId(), "FILING_EXPORTED",
                periodCode, now);
        return filings.list(query.provinceId(), query.regionCodes(), "APPROVED",
                query.endExclusive(), null, null);
    }

    public String optionName(String code) {
        return filings.optionName(code).orElse(code);
    }

    public DetailData detail(UserAccount actor, long filingId) {
        Org province = scopes.province(actor);
        Filing filing = visible(filingId, province, false);
        FilingVersion current = filings.version(filing.id(), filing.currentVersion()).orElseThrow();
        return new DetailData(id(filing.id()), id(filing.orgId()), filing.status(),
                filing.currentVersion(), region(filing.orgId()), current.profile(),
                current.submittedAt(), current.reviewedAt(),
                "RETURNED".equals(filing.status()) ? current.reason() : null,
                history(filing.id()));
    }

    public VersionData provinceVersion(UserAccount actor, long filingId, int version) {
        Org province = scopes.province(actor);
        validateVersion(version);
        Filing filing = visible(filingId, province, false);
        return version(filing.id(), version, regions.org(filing.orgId()).orElseThrow().regionCode());
    }

    @Transactional
    public ReviewData review(UserAccount actor, long filingId, Integer expectedVersion,
            boolean approve, String rawReason) {
        Org province = scopes.province(actor);
        if (expectedVersion == null || expectedVersion < 1) {
            throw field("expectedVersion", "版本号必须大于0");
        }
        String reason = trim(rawReason);
        if (!approve && (reason == null || reason.length() > 500)) {
            throw field("reason", "退回理由不能为空且最多500字");
        }
        Filing filing = visible(filingId, province, true);
        if (!"PENDING".equals(filing.status()) || filing.currentVersion() != expectedVersion) {
            throw new ConflictException("备案状态或版本已变化，请刷新后重试");
        }
        FilingVersion version = filings.version(filingId, expectedVersion).orElseThrow();
        LocalDateTime now = now();
        String result = approve ? "APPROVED" : "RETURNED";
        if (filings.setReviewed(filingId, expectedVersion, result, actor.id(),
                approve ? null : reason, now) != 1) {
            throw new ConflictException("备案状态或版本已变化，请刷新后重试");
        }
        if (approve) {
            filings.syncOrgName(filing.orgId(), version.profile().enterpriseName());
        }
        filings.audit(actor.id(), filing.orgId(), approve ? "FILING_APPROVED" : "FILING_RETURNED",
                Integer.toString(expectedVersion), now);
        return new ReviewData(id(filingId), result, expectedVersion,
                now.toInstant(ZoneOffset.UTC), approve ? null : reason, approve);
    }

    private Filing visible(long id, Org province, boolean lock) {
        Filing filing = filings.byId(id, lock).orElseThrow(NotFoundException::new);
        if (filing.currentVersion() == 0) {
            throw new NotFoundException();
        }
        scopes.visibleFiling(filing.orgId(), province);
        return filing;
    }

    private VersionData version(long id, int version, String regionCode) {
        FilingVersion v = filings.version(id, version).orElseThrow(NotFoundException::new);
        return new VersionData(FilingService.id(id), version, v.result(), v.profile(),
                regions.info(regionCode), v.submittedAt(), v.reviewedAt(), v.reason());
    }

    private void validateVersion(int version) {
        if (version < 1) {
            throw field("version", "版本号必须大于0");
        }
    }

    private List<History> history(long id) {
        return filings.history(id).stream().map(v -> new History(v.version(), v.result(),
                v.submittedAt(), v.reviewedAt(), v.reason())).toList();
    }

    private ListItem item(ListRow row) {
        return new ListItem(id(row.id()), id(row.orgId()), row.profile().enterpriseName(),
                row.profile().organizationCode(), regions.info(row.regionCode()), row.status(),
                row.currentVersion(), row.submittedAt(), row.reviewedAt());
    }

    private com.example.employment.backend.week2.shared.model.RegionInfo region(long orgId) {
        return regions.info(regions.org(orgId).orElseThrow().regionCode());
    }

    private Query query(UserAccount actor, String requestedStatus, String requestedRegion,
            String requestedPeriod) {
        Org province = scopes.province(actor);
        String status = requestedStatus == null || requestedStatus.isBlank()
                ? "PENDING" : requestedStatus;
        if (!"PENDING".equals(status) && !"APPROVED".equals(status)) {
            throw field("status", "仅支持PENDING或APPROVED");
        }
        String regionCode = trim(requestedRegion);
        if (regionCode != null) {
            if (regionCode.length() > 12 || regions.region(regionCode).isEmpty()) {
                throw field("regionCode", "地区不存在");
            }
            scopes.pathWithin(regionCode, province.regionCode());
        }
        String periodCode = trim(requestedPeriod);
        LocalDateTime end = null;
        if (periodCode != null) {
            if (!"APPROVED".equals(status)) {
                throw field("periodCode", "调查期仅适用于已通过备案");
            }
            Period period = filings.period(periodCode)
                    .orElseThrow(() -> field("periodCode", "调查期不存在"));
            end = LocalDateTime.ofInstant(period.endExclusiveAt(), ZoneOffset.UTC);
        }
        return new Query(province.id(), regions.descendants(
                regionCode == null ? province.regionCode() : regionCode),
                status, regionCode, periodCode, end);
    }

    private List<OptionParent> options(String category) {
        List<OptionRow> rows = filings.options(category);
        List<OptionParent> result = new ArrayList<>();
        for (OptionRow parent : rows) {
            if (parent.parentCode() == null) {
                result.add(new OptionParent(parent.code(), parent.name(), rows.stream()
                        .filter(child -> parent.code().equals(child.parentCode()))
                        .map(child -> new OptionChild(child.code(), child.name(), parent.code()))
                        .toList()));
            }
        }
        return result;
    }

    private Profile normalize(Profile p) {
        if (p == null) {
            throw field("profile", "请填写资料");
        }
        String code = trim(p.organizationCode());
        return new Profile(code == null ? null : code.toUpperCase(java.util.Locale.ROOT),
                trim(p.enterpriseName()), trim(p.natureParentCode()), trim(p.natureCode()),
                trim(p.industryParentCode()), trim(p.industryCode()), trim(p.mainBusiness()),
                trim(p.contactName()), trim(p.addressParentCode()), trim(p.addressCode()),
                trim(p.postalCode()), trim(p.contactPhone()), trim(p.fax()), trim(p.email()));
    }

    private void validate(Profile p, boolean required) {
        Map<String, String> errors = new LinkedHashMap<>();
        check(errors, "organizationCode", p.organizationCode(), "[A-Z0-9]{1,9}", required);
        length(errors, "enterpriseName", p.enterpriseName(), 120, required);
        length(errors, "mainBusiness", p.mainBusiness(), 500, required);
        length(errors, "contactName", p.contactName(), 80, required);
        check(errors, "postalCode", p.postalCode(), "[0-9]{6}", required);
        check(errors, "contactPhone", p.contactPhone(),
                "(1[3-9][0-9]{9}|0[0-9]{2,3}-[0-9]{7,8})", required);
        check(errors, "fax", p.fax(), "0[0-9]{2,3}-[0-9]{7,8}", required);
        if (p.email() != null && (p.email().length() > 254 ||
                !p.email().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) {
            errors.put("email", "邮箱格式不正确或超过254字");
        }
        pair(errors, "NATURE", "natureParentCode", p.natureParentCode(),
                "natureCode", p.natureCode(), required);
        pair(errors, "INDUSTRY", "industryParentCode", p.industryParentCode(),
                "industryCode", p.industryCode(), required);
        pair(errors, "ADDRESS", "addressParentCode", p.addressParentCode(),
                "addressCode", p.addressCode(), required);
        if (!errors.isEmpty()) {
            throw new FieldErrorException(errors);
        }
    }

    private void pair(Map<String, String> errors, String category, String parentField,
            String parent, String childField, String child, boolean required) {
        if (parent == null) {
            if (required) errors.put(parentField, "必填");
            if (child != null) errors.put(childField, "请先选择一级选项");
            return;
        }
        boolean parentExists = filings.options(category).stream()
                .anyMatch(row -> row.code().equals(parent) && row.parentCode() == null);
        if (!parentExists) {
            errors.put(parentField, "一级选项不存在");
        }
        if (child == null) {
            if (required) errors.put(childField, "必填");
        } else if (!filings.optionPair(category, parent, child)) {
            errors.put(childField, "二级选项与一级选项不匹配");
        }
    }

    private static void check(Map<String, String> errors, String field,
            String value, String regex, boolean required) {
        if (value == null) {
            if (required) errors.put(field, "必填");
        } else if (!value.matches(regex)) {
            errors.put(field, "格式不正确");
        }
    }

    private static void length(Map<String, String> errors, String field,
            String value, int max, boolean required) {
        if (value == null) {
            if (required) errors.put(field, "必填");
        } else if (value.length() > max) {
            errors.put(field, "最多" + max + "字");
        }
    }

    private static String trim(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static boolean editable(String status) {
        return "DRAFT".equals(status) || "RETURNED".equals(status);
    }

    private static String id(long value) { return Long.toString(value); }
    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
    }
    private static FieldErrorException field(String name, String message) {
        return new FieldErrorException(Map.of(name, message));
    }

    private record Query(long provinceId, List<String> regionCodes, String status,
            String regionCode, String periodCode, LocalDateTime endExclusive) {
    }
}
