package com.example.employment.backend.week1.identity.model;

import java.util.List;

public record UserSummary(String id, String displayName, String roleCode,
        String orgId, String orgName, List<UserAccount.FunctionalRole> functionalRoles,
        List<String> permissions) {

    public static UserSummary from(UserAccount account) {
        return new UserSummary(Long.toString(account.id()), account.displayName(),
                account.roleCodes().getFirst(), Long.toString(account.orgId()), account.orgName(),
                account.functionalRoles(), account.permissions());
    }
}
