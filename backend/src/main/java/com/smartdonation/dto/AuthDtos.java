package com.smartdonation.dto;

import com.smartdonation.entity.BloodGroup;
import com.smartdonation.entity.UserRole;
import java.time.LocalDate;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            String fullName,
            String email,
            String password,
            UserRole role,
            BloodGroup bloodGroup,
            String phone,
            String city,
            double latitude,
            double longitude,
            boolean bloodDonor,
            boolean organDonor,
            boolean availableForDonation,
            LocalDate lastDonationDate) {}

    public record LoginRequest(String email, String password) {}
    public record LoginResponse(boolean authenticated, UserResponse user) {}
}
