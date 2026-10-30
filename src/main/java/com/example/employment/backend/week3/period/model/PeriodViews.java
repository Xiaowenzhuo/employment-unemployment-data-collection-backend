package com.example.employment.backend.week3.period.model;

import java.time.Instant;
import java.util.List;

public final class PeriodViews {
    private PeriodViews() { }

    public record PeriodData(String code, String label, Instant startAt, Instant endExclusiveAt,
            Instant openAt, Instant closeAt, boolean demo, boolean legacy,
            int currentVersion, String windowStatus) { }

    public record ProvinceList(List<PeriodData> items, long total) { }

    public record EnterprisePeriod(String code, String label, Instant startAt, Instant endExclusiveAt,
            Instant openAt, Instant closeAt, boolean demo, boolean legacy,
            int currentVersion, String windowStatus, boolean filingEligible,
            boolean canWrite, String blockedReason) { }

    public record EnterpriseList(List<EnterprisePeriod> items) { }

    public record PeriodInput(String code, String label, Instant startAt, Instant endExclusiveAt,
            Instant openAt, Instant closeAt, Boolean demo, Integer expectedVersion) { }
}
