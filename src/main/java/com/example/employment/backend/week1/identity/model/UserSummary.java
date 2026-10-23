package com.example.employment.backend.week1.identity.model;

public record UserSummary(String id, String displayName, String roleCode,
        String orgId, String orgName) {

    public static UserSummary from(UserAccount account) {
        return new UserSummary(Long.toString(account.id()), account.displayName(),
                account.roleCodes().getFirst(), Long.toString(account.orgId()), account.orgName());
    }
}
