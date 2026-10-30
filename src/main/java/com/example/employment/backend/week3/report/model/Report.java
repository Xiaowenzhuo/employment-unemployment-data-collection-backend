package com.example.employment.backend.week3.report.model;

import java.time.Instant;

public record Report(long id, long orgId, String periodCode, String status, int currentVersion,
        int submissionVersion, ReportForm form, Instant updatedAt, Instant submittedAt) { }
