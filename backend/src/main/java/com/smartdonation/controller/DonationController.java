package com.smartdonation.controller;

import com.smartdonation.dto.DonationHistoryRequest;
import com.smartdonation.dto.DonationHistoryResponse;
import com.smartdonation.service.DonationService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/donations")
public class DonationController {
    private final DonationService donationService;

    public DonationController(DonationService donationService) {
        this.donationService = donationService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DONOR', 'ADMIN')")
    public DonationHistoryResponse record(@RequestBody DonationHistoryRequest request, Authentication authentication) {
        return donationService.record(request, authentication.getName(), isAdmin(authentication));
    }

    @GetMapping("/donor/{donorId}")
    @PreAuthorize("hasAnyRole('DONOR', 'ADMIN')")
    public List<DonationHistoryResponse> byDonor(@PathVariable Long donorId, Authentication authentication) {
        return donationService.byDonor(donorId, authentication.getName(), isAdmin(authentication));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
