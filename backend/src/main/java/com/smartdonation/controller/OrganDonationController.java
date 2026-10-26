package com.smartdonation.controller;

import com.smartdonation.dto.OrganPreferenceRequest;
import com.smartdonation.dto.OrganPreferenceResponse;
import com.smartdonation.service.OrganDonationService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/organs")
public class OrganDonationController {
    private final OrganDonationService organDonationService;

    public OrganDonationController(OrganDonationService organDonationService) {
        this.organDonationService = organDonationService;
    }

    @PostMapping("/preferences")
    @PreAuthorize("hasAnyRole('DONOR', 'ADMIN')")
    public OrganPreferenceResponse savePreference(@RequestBody OrganPreferenceRequest request, Authentication authentication) {
        return organDonationService.savePreference(request, authentication.getName(), isAdmin(authentication));
    }

    @GetMapping("/preferences/me")
    @PreAuthorize("hasRole('DONOR')")
    public ResponseEntity<OrganPreferenceResponse> myPreference(Authentication authentication) {
        var preference = organDonationService.findPreferenceForDonor(authentication.getName());
        return preference.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().<OrganPreferenceResponse>build());
    }

    @PostMapping("/preferences/{id}/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public OrganPreferenceResponse verify(@PathVariable Long id) {
        return organDonationService.verify(id);
    }

    @GetMapping("/preferences")
    @PreAuthorize("hasRole('ADMIN')")
    public List<OrganPreferenceResponse> list() {
        return organDonationService.list();
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
