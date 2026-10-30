package com.example.employment.backend.week4.review.service;

import com.example.employment.backend.common.ConflictException;
import com.example.employment.backend.common.FieldErrorException;
import com.example.employment.backend.common.ForbiddenException;
import com.example.employment.backend.common.NotFoundException;
import com.example.employment.backend.week1.identity.model.UserAccount;
import com.example.employment.backend.week2.filing.repository.FilingRepository;
import com.example.employment.backend.week2.shared.dao.RegionScopeDao.Org;
import com.example.employment.backend.week2.shared.repository.RegionScopeRepository;
import com.example.employment.backend.week2.shared.service.ScopeService;
import com.example.employment.backend.week3.period.model.SurveyPeriod;
import com.example.employment.backend.week3.period.repository.PeriodRepository;
import com.example.employment.backend.week3.report.model.Report;
import com.example.employment.backend.week3.report.model.ReportViews.Snapshot;
import com.example.employment.backend.week3.report.repository.ReportRepository;
import com.example.employment.backend.week4.review.dao.ReviewDao.ReviewRow;
import com.example.employment.backend.week4.review.model.ReviewViews.*;
import com.example.employment.backend.week4.review.repository.ReviewRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {
    private static final List<String> STATUSES = List.of("SUBMITTED_CITY", "SUBMITTED_PROVINCE",
            "RETURNED_CITY", "RETURNED_PROVINCE", "APPROVED_PROVINCE");
    private final ReviewRepository reviews;
    private final ReportRepository reports;
    private final PeriodRepository periods;
    private final RegionScopeRepository regions;
    private final ScopeService scopes;
    private final FilingRepository filings;

    public ReviewService(ReviewRepository reviews, ReportRepository reports,
            PeriodRepository periods, RegionScopeRepository regions, ScopeService scopes,
            FilingRepository filings) {
        this.reviews = reviews;
        this.reports = reports;
        this.periods = periods;
        this.regions = regions;
        this.scopes = scopes;
        this.filings = filings;
    }

    public ReviewList list(UserAccount actor, String level, String periodCode, String status,
            int page, int size) {
        Scope scope = scope(actor, level);
        if (page < 0 || (long) page * size > Integer.MAX_VALUE) throw field("page", "页码不合法");
        if (size < 1 || size > 100) throw field("size", "每页应为1至100条");
        String filterStatus = status == null ? pending(level) : status;
        if (!STATUSES.contains(filterStatus)) throw field("status", "状态不合法");
        if (periodCode != null) {
            SurveyPeriod period = periods.byCode(periodCode, false)
                    .orElseThrow(() -> field("periodCode", "调查期不存在"));
            if (period.provinceOrgId() != null && period.provinceOrgId() != scope.provinceId()) {
                throw field("periodCode", "调查期不存在");
            }
        }
        List<ReviewListItem> items = reviews.list(level, scope.orgId(), periodCode,
                filterStatus, size, page * size).stream().map(row -> item(row, level)).toList();
        return new ReviewList(items, reviews.count(level, scope.orgId(), periodCode,
                filterStatus), page, size, new ReviewFilters(periodCode, filterStatus));
    }

    public ReviewDetail detail(UserAccount actor, String level, String reportId) {
        Scope scope = scope(actor, level);
        ReviewRow row = reviews.scoped(id(reportId), level, scope.orgId())
                .orElseThrow(NotFoundException::new);
        ReviewListItem item = item(row, level);
        List<Snapshot> snapshots = reports.snapshots(row.reportId());
        Snapshot current = snapshots.stream().filter(s -> s.submissionVersion() == row.submissionVersion())
                .findFirst().orElseThrow(IllegalStateException::new);
        return new ReviewDetail(item.reportId(), item.periodCode(), item.periodLabel(),
                item.orgId(), item.enterpriseName(), item.organizationCode(), item.cityOrgId(),
                item.cityName(), item.regionCode(), item.regionName(), item.status(),
                item.currentVersion(), item.submissionVersion(), item.baselineEmploymentCount(),
                item.periodEmploymentCount(), item.lastActivityAt(), item.submittedAt(),
                item.canReview(), item.latestReturn(), current.form(), current, snapshots,
                reviews.events(row.reportId()));
    }

    @Transactional
    public ReviewResult decide(UserAccount actor, String level, String reportId,
            String action, Integer expectedVersion, Integer submissionVersion, String comment) {
        Scope scope = scope(actor, level);
        if (expectedVersion == null || expectedVersion < 1) {
            throw field("expectedVersion", "版本号必须为正整数");
        }
        if (submissionVersion == null || submissionVersion < 1) {
            throw field("submissionVersion", "提交版本号必须为正整数");
        }
        String reason = comment == null || comment.trim().isEmpty() ? null : comment.trim();
        if (reason != null && reason.length() > 500) throw field("comment", "最多500字");
        long id = id(reportId);
        Report report = reports.byId(id, true).orElseThrow(NotFoundException::new);
        reviews.scoped(id, level, scope.orgId()).orElseThrow(NotFoundException::new);
        String from = pending(level);
        if (!from.equals(report.status()) || report.currentVersion() != expectedVersion
                || report.submissionVersion() != submissionVersion) {
            throw new ConflictException("报表状态或版本已变化，请刷新后重试");
        }
        String to = "APPROVE".equals(action)
                ? ("CITY".equals(level) ? "SUBMITTED_PROVINCE" : "APPROVED_PROVINCE")
                : ("CITY".equals(level) ? "RETURNED_CITY" : "RETURNED_PROVINCE");
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
        if (reviews.changeStatus(id, from, to, expectedVersion, submissionVersion, now) != 1) {
            throw new ConflictException("报表状态或版本已变化，请刷新后重试");
        }
        long eventId = reviews.nextId();
        reviews.insertEvent(eventId, id, submissionVersion, level, action, from, to,
                actor.id(), actor.orgId(), actor.displayName(), now, reason);
        filings.audit(actor.id(), report.orgId(), "REPORT_" + level + "_" + action,
                report.periodCode(), now);
        ReviewEvent event = new ReviewEvent(Long.toString(eventId), level, action, from, to,
                submissionVersion, Long.toString(actor.id()), Long.toString(actor.orgId()),
                actor.displayName(), now.toInstant(ZoneOffset.UTC), reason);
        return new ReviewResult(reportId, report.periodCode(), to, expectedVersion + 1,
                submissionVersion, event);
    }

    private ReviewListItem item(ReviewRow row, String level) {
        return new ReviewListItem(Long.toString(row.reportId()), row.periodCode(),
                row.periodLabel(), Long.toString(row.orgId()), row.enterpriseName(),
                row.organizationCode(), Long.toString(row.cityOrgId()), row.cityName(),
                row.regionCode(), row.regionName(), row.status(), row.currentVersion(),
                row.submissionVersion(), row.baselineEmploymentCount(), row.periodEmploymentCount(),
                row.lastActivityAt(), row.submittedAt(), pending(level).equals(row.status()),
                latestReturn(row.reportId(), row.status()));
    }

    public LatestReturn latestReturn(long reportId, String status) {
        if (!"RETURNED_CITY".equals(status) && !"RETURNED_PROVINCE".equals(status)) return null;
        return reviews.latestReturn(reportId).orElse(null);
    }

    private Scope scope(UserAccount actor, String level) {
        if ("PROVINCE".equals(level)) {
            Org province = scopes.province(actor);
            return new Scope(province.id(), province.id());
        }
        if (!"CITY".equals(level)) throw new ForbiddenException();
        Org city = regions.org(actor.orgId()).orElseThrow(ForbiddenException::new);
        if (!city.enabled() || !"CITY".equals(city.type()) || city.parentId() == null) {
            throw new ForbiddenException();
        }
        Org province = regions.org(city.parentId()).orElseThrow(ForbiddenException::new);
        if (!"PROVINCE".equals(province.type())) throw new ForbiddenException();
        return new Scope(city.id(), province.id());
    }

    private static String pending(String level) {
        return "CITY".equals(level) ? "SUBMITTED_CITY" : "SUBMITTED_PROVINCE";
    }

    private static long id(String raw) {
        if (raw == null || !raw.matches("[0-9]+")) {
            throw field("reportId", "报表编号应为正整数");
        }
        try {
            long id = Long.parseLong(raw);
            if (id > 0) return id;
        } catch (NumberFormatException ignored) { }
        throw field("reportId", "报表编号应为正整数");
    }

    private static FieldErrorException field(String name, String message) {
        return new FieldErrorException(Map.of(name, message));
    }

    private record Scope(long orgId, long provinceId) { }
}
