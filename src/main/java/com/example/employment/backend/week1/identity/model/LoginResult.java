package com.example.employment.backend.week1.identity.model;

public record LoginResult(String accessToken, long expiresInSeconds, UserSummary user) {
}
