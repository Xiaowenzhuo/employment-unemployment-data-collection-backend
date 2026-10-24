package com.example.employment.backend.week2.account.model;

public record CreateAccountRequest(String username, String initialPassword,
        String orgName, String regionCode) {
}
