package com.smartdonation.dto;

public record AdminDashboardResponse(
        long totalUsers,
        long totalBloodDonors,
        long totalOrganDonors,
        long activeBloodRequests,
        long verifiedOrganDonors) {}
