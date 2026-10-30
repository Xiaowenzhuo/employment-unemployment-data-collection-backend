package com.example.employment.backend.week3.period.model;

import java.time.Instant;

public record SurveyPeriod(String code, String label, Instant startAt, Instant endExclusiveAt,
        Instant openAt, Instant closeAt, boolean demo, Long provinceOrgId, int currentVersion) {
    public boolean legacy() { return provinceOrgId == null; }

    public String windowStatus(Instant now) {
        if (legacy()) return "UNAVAILABLE";
        if (now.isBefore(openAt)) return "NOT_OPEN";
        if (!now.isBefore(closeAt)) return "CLOSED";
        return "OPEN";
    }
}
