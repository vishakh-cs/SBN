package com.smartdonation.service;

import com.smartdonation.dto.AuthDtos.RegisterRequest;
import com.smartdonation.dto.DonorProfileUpdateRequest;
import com.smartdonation.dto.UserResponse;
import com.smartdonation.entity.UserAccount;
import com.smartdonation.entity.UserRole;
import com.smartdonation.repository.UserAccountRepository;
import java.util.List;
import java.time.LocalDate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserService {
    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserAccountRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse register(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Registration payload is required");
        }
        if (!StringUtils.hasText(request.email())) {
            throw new IllegalArgumentException("Email is required");
        }
        if (!StringUtils.hasText(request.password())) {
            throw new IllegalArgumentException("Password is required");
        }
        String normalizedEmail = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email is already registered");
        }

        UserRole normalizedRole = publicRegistrationRole(request.role());

        UserAccount user = new UserAccount();
        user.setFullName(request.fullName());
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(normalizedRole);
        user.setBloodGroup(request.bloodGroup());
        user.setPhone(request.phone());
        user.setCity(request.city());
        user.setLatitude(request.latitude());
        user.setLongitude(request.longitude());
        user.setBloodDonor(request.bloodDonor());
        user.setOrganDonor(request.organDonor());
        user.setAvailableForDonation(request.availableForDonation());
        user.setLastDonationDate(request.lastDonationDate());
        return UserResponse.from(userRepository.save(user));
    }

    private UserRole publicRegistrationRole(UserRole requestedRole) {
        if (requestedRole == null) {
            return UserRole.DONOR;
        }
        if (requestedRole != UserRole.DONOR && requestedRole != UserRole.PATIENT) {
            throw new IllegalArgumentException("Public registration supports DONOR or PATIENT accounts only");
        }
        return requestedRole;
    }

    public UserResponse profile(String email) {
        UserAccount user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated account no longer exists"));
        return UserResponse.from(user);
    }

    public UserResponse updateBloodAvailability(String authenticatedEmail, boolean available) {
        UserAccount user = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated account no longer exists"));
        if (user.getRole() != UserRole.DONOR) {
            throw new org.springframework.security.access.AccessDeniedException("Only donors can change blood availability");
        }
        if (available && !user.isBloodDonor()) {
            throw new IllegalArgumentException("Enable blood donor status in your profile before becoming available");
        }
        user.setAvailableForDonation(available);
        return UserResponse.from(userRepository.save(user));
    }

    public UserResponse updateDonorProfile(String authenticatedEmail, DonorProfileUpdateRequest request) {
        if (request == null) throw new IllegalArgumentException("Profile details are required");
        UserAccount user = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated account no longer exists"));
        if (user.getRole() != UserRole.DONOR) {
            throw new org.springframework.security.access.AccessDeniedException("Only donors can edit donor profiles");
        }
        if (!StringUtils.hasText(request.fullName()) || request.fullName().trim().length() > 120) {
            throw new IllegalArgumentException("Name is required and must be 120 characters or fewer");
        }
        if (request.bloodGroup() == null) {
            throw new IllegalArgumentException("Blood group is required");
        }
        String phone = request.phone() == null ? null : request.phone().trim();
        String city = request.city() == null ? null : request.city().trim();
        if (phone != null && phone.length() > 30) throw new IllegalArgumentException("Phone must be 30 characters or fewer");
        if (city != null && city.length() > 120) throw new IllegalArgumentException("City must be 120 characters or fewer");
        LocalDate lastDonationDate = request.lastDonationDate();
        if (lastDonationDate != null && lastDonationDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Last donation date cannot be in the future");
        }

        user.setFullName(request.fullName().trim());
        user.setBloodGroup(request.bloodGroup());
        user.setPhone(phone == null || phone.isBlank() ? null : phone);
        user.setCity(city == null || city.isBlank() ? null : city);
        user.setLastDonationDate(lastDonationDate);
        return UserResponse.from(userRepository.save(user));
    }

    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }
}
