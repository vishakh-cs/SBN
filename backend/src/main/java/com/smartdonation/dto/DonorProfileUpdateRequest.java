package com.smartdonation.dto;

import com.smartdonation.entity.BloodGroup;
import java.time.LocalDate;

public record DonorProfileUpdateRequest(
        String fullName,
        BloodGroup bloodGroup,
        String phone,
        String city,
        LocalDate lastDonationDate) {}
