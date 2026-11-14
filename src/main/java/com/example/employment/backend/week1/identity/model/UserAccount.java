package com.example.employment.backend.week1.identity.model;

import java.util.List;

public record UserAccount(long id, long orgId, String username, String passwordHash,
        String displayName, boolean enabled, String orgName, boolean orgEnabled,
        List<String> roleCodes, List<FunctionalRole> functionalRoles,
        List<String> permissions, long authVersion, boolean deleted) {

    public boolean canLogin() {
        return enabled && orgEnabled && !deleted && roleCodes.size() == 1
                && !functionalRoles.isEmpty();
    }

    public record FunctionalRole(String id, String code, String name, String level) {
    }
}
