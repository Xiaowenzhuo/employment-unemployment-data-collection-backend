package com.example.employment.backend.week1.identity.model;

import java.util.List;

public record UserAccount(long id, long orgId, String username, String passwordHash,
        String displayName, boolean enabled, String orgName, boolean orgEnabled,
        List<String> roleCodes) {

    public boolean canLogin() {
        return enabled && orgEnabled && roleCodes.size() == 1;
    }
}
