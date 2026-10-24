package com.example.employment.backend.week2.filing.model;

public record Profile(String organizationCode, String enterpriseName,
        String natureParentCode, String natureCode, String industryParentCode,
        String industryCode, String mainBusiness, String contactName,
        String addressParentCode, String addressCode, String postalCode,
        String contactPhone, String fax, String email) {
}
