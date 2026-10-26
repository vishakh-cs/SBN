package com.smartdonation.dto;

import com.smartdonation.entity.BloodGroup;

public final class BloodRequestDtos {
    private BloodRequestDtos() {}

    public record CreateBloodRequest(
            String requesterName,
            String contactEmail,
            String contactPhone,
            String hospitalName,
            String city,
            double latitude,
            double longitude,
            BloodGroup requiredBloodGroup,
            int unitsRequired,
            String urgency,
            String notes) {}
}
