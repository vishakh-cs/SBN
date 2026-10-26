package com.smartdonation.dto;

import com.smartdonation.entity.DonationHistory;
import java.time.LocalDate;

public record DonationHistoryResponse(Long id, Long donorId, Long bloodRequestId, LocalDate donatedOn,
                                      String donationType, String location, String notes) {
    public static DonationHistoryResponse from(DonationHistory history) {
        return new DonationHistoryResponse(history.getId(), history.getDonor().getId(),
                history.getBloodRequest() == null ? null : history.getBloodRequest().getId(), history.getDonatedOn(),
                history.getDonationType(), history.getLocation(), history.getNotes());
    }
}
