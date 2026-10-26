package com.smartdonation.dto;

import com.smartdonation.entity.BloodGroup;

public record DonorMatchResponse(
        Long donorId,
        String fullName,
        String email,
        String phone,
        String city,
        BloodGroup bloodGroup,
        double distanceKm,
        int score) {}
