package com.smartdonation.dto;

import com.smartdonation.entity.BloodGroup;
import com.smartdonation.entity.UserAccount;
import com.smartdonation.entity.UserRole;
import java.time.LocalDate;

public record UserResponse(
        Long id,
        String fullName,
        String email,
        UserRole role,
        BloodGroup bloodGroup,
        String phone,
        String city,
        double latitude,
        double longitude,
        boolean bloodDonor,
        boolean organDonor,
        boolean availableForDonation,
        LocalDate lastDonationDate) {
    public static UserResponse from(UserAccount user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getBloodGroup(),
                user.getPhone(),
                user.getCity(),
                user.getLatitude(),
                user.getLongitude(),
                user.isBloodDonor(),
                user.isOrganDonor(),
                user.isAvailableForDonation(),
                user.getLastDonationDate());
    }
}
