package com.example.employment.backend.week2.filing.model;

import java.time.Instant;

public record Filing(long id, long orgId, String status, int currentVersion,
        Profile profile, Instant updatedAt) {
}
