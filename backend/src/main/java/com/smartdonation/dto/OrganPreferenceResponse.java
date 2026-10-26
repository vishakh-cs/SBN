package com.smartdonation.dto;

import com.smartdonation.entity.OrganDonationPreference;
import java.time.LocalDateTime;

public record OrganPreferenceResponse(Long id, Long donorId, String organs, String consentStatement,
                                     boolean verified, LocalDateTime registeredAt) {
    public static OrganPreferenceResponse from(OrganDonationPreference preference) {
        return new OrganPreferenceResponse(preference.getId(), preference.getDonor().getId(), preference.getOrgans(),
                preference.getConsentStatement(), preference.isVerified(), preference.getRegisteredAt());
    }
}
