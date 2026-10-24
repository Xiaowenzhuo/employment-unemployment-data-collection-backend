package com.example.employment.backend.week2.filing.model;

import com.example.employment.backend.week2.shared.model.RegionInfo;
import java.time.Instant;
import java.util.List;

public final class FilingViews {
    private FilingViews() {
    }

    public record ProfileData(String filingId, String status, int currentVersion,
            boolean editable, RegionInfo region, Profile profile, Instant updatedAt) {
    }

    public record History(int version, String result, Instant submittedAt,
            Instant reviewedAt, String reason) {
    }

    public record StatusData(String filingId, String status, int currentVersion,
            boolean canReport, Instant submittedAt, Instant reviewedAt,
            String returnReason, List<History> history) {
    }

    public record SubmitData(String filingId, String status, int currentVersion,
            Instant submittedAt, boolean canReport) {
    }

    public record VersionData(String filingId, int version, String result,
            Profile profile, RegionInfo region, Instant submittedAt,
            Instant reviewedAt, String reason) {
    }

    public record ListItem(String filingId, String orgId, String enterpriseName,
            String organizationCode, RegionInfo region, String status,
            int currentVersion, Instant submittedAt, Instant reviewedAt) {
    }

    public record Filters(String status, String regionCode, String periodCode) {
    }

    public record ListData(List<ListItem> items, long total, int page, int size,
            Filters filters) {
    }

    public record DetailData(String filingId, String orgId, String status,
            int currentVersion, RegionInfo region, Profile profile,
            Instant submittedAt, Instant reviewedAt, String returnReason,
            List<History> history) {
    }

    public record ReviewData(String filingId, String status, int currentVersion,
            Instant reviewedAt, String returnReason, boolean canReport) {
    }

    public record OptionChild(String code, String name, String parentCode) {
    }

    public record OptionParent(String code, String name, List<OptionChild> children) {
    }

    public record OptionsData(List<OptionParent> natures, List<OptionParent> industries,
            List<OptionParent> addresses, boolean demo) {
    }

    public record Period(String code, String label, Instant startAt,
            Instant endExclusiveAt, boolean demo) {
    }
}
