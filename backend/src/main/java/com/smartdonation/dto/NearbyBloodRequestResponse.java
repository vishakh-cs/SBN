package com.smartdonation.dto;

import com.smartdonation.entity.BloodGroup;
import java.time.LocalDateTime;

public record NearbyBloodRequestResponse(
        Long id,
        BloodGroup requiredBloodGroup,
        String hospitalName,
        String city,
        String urgency,
        int unitsRequired,
        LocalDateTime createdAt,
        double distanceKm) {}
