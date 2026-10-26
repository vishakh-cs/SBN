package com.smartdonation.controller;

import com.smartdonation.dto.UserResponse;
import com.smartdonation.dto.AvailabilityUpdateRequest;
import com.smartdonation.dto.DonorProfileUpdateRequest;
import com.smartdonation.service.UserService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> list() {
        return userService.listUsers();
    }

    @PatchMapping("/me/availability")
    @PreAuthorize("hasRole('DONOR')")
    public UserResponse updateAvailability(@RequestBody AvailabilityUpdateRequest request, Authentication authentication) {
        return userService.updateBloodAvailability(authentication.getName(), request.available());
    }

    @PatchMapping("/me/profile")
    @PreAuthorize("hasRole('DONOR')")
    public UserResponse updateDonorProfile(@RequestBody DonorProfileUpdateRequest request, Authentication authentication) {
        return userService.updateDonorProfile(authentication.getName(), request);
    }
}
