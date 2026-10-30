package com.example.employment.backend.week3.report.model;

public record ReportForm(Integer baselineEmploymentCount, Integer periodEmploymentCount,
        String otherReason, String reductionTypeCode, String primaryReasonCode,
        String primaryReasonDetail, String secondaryReasonCode, String secondaryReasonDetail,
        String tertiaryReasonCode, String tertiaryReasonDetail) { }
