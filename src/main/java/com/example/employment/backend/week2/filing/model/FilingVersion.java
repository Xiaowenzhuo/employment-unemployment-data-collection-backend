package com.example.employment.backend.week2.filing.model;

import java.time.Instant;

public record FilingVersion(long filingId, int version, String result, Profile profile,
        Instant submittedAt, Instant reviewedAt, String reason) {
}
