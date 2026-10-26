package com.smartdonation.dto;

import java.time.LocalDate;

public record DonationHistoryRequest(Long donorId, Long bloodRequestId, LocalDate donatedOn, String donationType, String location, String notes) {}
