package com.example.employment.backend.week4.review.model;

import com.example.employment.backend.week3.report.model.ReportForm;
import com.example.employment.backend.week3.report.model.ReportViews.Snapshot;
import java.time.Instant;
import java.util.List;

public final class ReviewViews {
    private ReviewViews() { }

    public record LatestReturn(String level, String comment, Instant reviewedAt,
            String reviewerUserId, String reviewerDisplayName) { }

    public record ReviewEvent(String eventId, String level, String action, String fromStatus,
            String toStatus, int submissionVersion, String reviewerUserId, String reviewerOrgId,
            String reviewerDisplayName, Instant reviewedAt, String comment) { }

    public record ReviewListItem(String reportId, String periodCode, String periodLabel,
            String orgId, String enterpriseName, String organizationCode, String cityOrgId,
            String cityName, String regionCode, String regionName, String status,
            int currentVersion, int submissionVersion, Integer baselineEmploymentCount,
            Integer periodEmploymentCount, Instant lastActivityAt, Instant submittedAt,
            boolean canReview, LatestReturn latestReturn) { }

    public record ReviewFilters(String periodCode, String status) { }

    public record ReviewList(List<ReviewListItem> items, long total, int page, int size,
            ReviewFilters filters) { }

    public record ReviewDetail(String reportId, String periodCode, String periodLabel,
            String orgId, String enterpriseName, String organizationCode, String cityOrgId,
            String cityName, String regionCode, String regionName, String status,
            int currentVersion, int submissionVersion, Integer baselineEmploymentCount,
            Integer periodEmploymentCount, Instant lastActivityAt, Instant submittedAt,
            boolean canReview, LatestReturn latestReturn, ReportForm form,
            Snapshot currentSnapshot, List<Snapshot> snapshots, List<ReviewEvent> reviewEvents) { }

    public record ReviewResult(String reportId, String periodCode, String status,
            int currentVersion, int submissionVersion, ReviewEvent event) { }
}
