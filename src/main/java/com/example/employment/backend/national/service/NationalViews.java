package com.example.employment.backend.national.service;

import java.time.Instant;
import java.util.List;

public final class NationalViews {
    private NationalViews() { }

    public record Period(String periodCode, String periodLabel, Instant startAt,
            Instant endExclusiveAt, Instant closeAt, String windowStatus, boolean legacy) { }
    public record Blocker(String code, String message) { }
    public record Counts(long approvedCount, long pendingCityCount, long pendingProvinceCount,
            long returnedCityCount, long returnedProvinceCount, long voidedCount,
            long missingCount) { }
    public record Missing(String enterpriseOrgId, String enterpriseName, String cityCode,
            String regionCode, String reportStatus) { }
    public record Summary(int enterpriseCount, long baselineEmploymentTotal,
            long periodEmploymentTotal, long netChange) { }
    public record City(String cityCode, String cityName, int enterpriseCount,
            long baselineEmploymentTotal, long periodEmploymentTotal, long netChange) { }
    public record Enterprise(String organizationCode, String enterpriseName, String cityCode,
            String regionCode, String natureCode, String industryCode, String reportId,
            int submissionVersion, int currentVersion, String baselineSourcePeriodCode,
            int baselineEmploymentCount, int periodEmploymentCount, int netChange,
            String reductionTypeCode, String primaryReasonCode, String primaryReasonDetail,
            String otherReason) { }
    public record Preview(Summary summary, List<City> cities, List<Enterprise> enterprises) { }
    public record ExistingBatch(String batchId, String status) { }
    public record Readiness(Period period, boolean canSubmit, List<Blocker> blockers,
            boolean requiresMissingAcknowledgement, Counts counts,
            List<Missing> missingEnterprises, Preview preview, ExistingBatch existingBatch) { }
    public record Payload(String schemaVersion, String batchId, String mode,
            String provinceCode, String periodCode, Instant periodStartAt,
            Instant periodEndExclusiveAt, Instant generatedAt, Summary summary,
            List<City> cities, List<Enterprise> enterprises) { }
    public record QueueResult(String batchId, String status, String mode,
            int enterpriseCount, String detailPath) { }
    public record BatchSummary(String batchId, String periodCode, String mode,
            String status, int enterpriseCount, String payloadSha256, Instant createdAt,
            Instant lastSentAt, int retryCount, String lastError) { }
    public record Receipt(String batchId, String mockReceiptId, String result,
            Instant receivedAt, int acceptedEnterpriseCount, String payloadSha256) { }
    public record Attempt(int attemptNo, Instant startedAt, Instant finishedAt, String result,
            String errorSummary, String mockReceiptId) { }
    public record BatchDetail(String batchId, String periodCode, String mode,
            String status, int enterpriseCount, String payloadSha256, Instant createdAt,
            Instant lastSentAt, int retryCount, String lastError, Payload payload,
            List<Attempt> attempts, Receipt receipt) { }
    public record BatchList(List<BatchSummary> items, long total, int page, int size) { }
    public record Card(String periodCode, String state, String label, String route,
            String batchId, boolean canStart, boolean canRetry) { }
}
