package com.example.employment.backend.week3.report.model;

import java.time.Instant;
import java.util.List;

public final class ReportViews {
    private ReportViews() { }

    public record ReportView(String reportId, String periodCode, String status,
            int currentVersion, int submissionVersion, boolean filingEligible,
            String windowStatus, boolean canWrite, String blockedReason,
            boolean baselineLocked, Integer lockedBaselineEmploymentCount,
            ReportForm form, Instant updatedAt, Instant submittedAt) { }

    public record SubmitResult(String reportId, String periodCode, String status,
            int currentVersion, int submissionVersion, Instant submittedAt) { }

    public record ListItem(String reportId, String periodCode, String periodLabel,
            String status, int currentVersion, int submissionVersion, Instant submittedAt) { }

    public record ReportList(List<ListItem> items, long total, int page, int size) { }

    public record Snapshot(int submissionVersion, ReportForm form, Instant submittedAt,
            String submittedBy, String result) { }

    public record History(String reportId, String periodCode, String status,
            List<Snapshot> snapshots) { }

    public record Option(String code, String name) { }

    public record ReasonChild(String code, String name, String parentCode) { }

    public record Reason(String code, String name, String parentTypeCode,
            String parentCode, List<ReasonChild> children) { }

    public record Options(List<Option> reductionTypes, List<Reason> reasons) { }
}
