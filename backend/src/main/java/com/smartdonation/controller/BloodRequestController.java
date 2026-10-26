package com.smartdonation.controller;

import com.smartdonation.dto.BloodRequestDtos.CreateBloodRequest;
import com.smartdonation.dto.DonorMatchResponse;
import com.smartdonation.dto.NearbyBloodRequestResponse;
import com.smartdonation.entity.BloodRequest;
import com.smartdonation.service.BloodRequestService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/blood-requests")
public class BloodRequestController {
    private final BloodRequestService bloodRequestService;

    public BloodRequestController(BloodRequestService bloodRequestService) {
        this.bloodRequestService = bloodRequestService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PATIENT', 'HOSPITAL', 'ADMIN')")
    public BloodRequest create(@RequestBody CreateBloodRequest request, Authentication authentication) {
        return bloodRequestService.create(request, authentication.getName());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PATIENT', 'HOSPITAL', 'ADMIN')")
    public List<BloodRequest> list(Authentication authentication) {
        return bloodRequestService.list(authentication.getName(), isAdmin(authentication));
    }

    @GetMapping("/nearby")
    @PreAuthorize("hasRole('DONOR')")
    public List<NearbyBloodRequestResponse> nearby(Authentication authentication) {
        return bloodRequestService.nearbyForDonor(authentication.getName());
    }

    @PostMapping("/{id}/match")
    @PreAuthorize("hasAnyRole('PATIENT', 'HOSPITAL', 'ADMIN')")
    public List<DonorMatchResponse> matchAndNotify(@PathVariable Long id, Authentication authentication) {
        return bloodRequestService.matchAndNotify(id, authentication.getName(), isAdmin(authentication));
    }

    @GetMapping("/{id}/matches")
    @PreAuthorize("hasAnyRole('PATIENT', 'HOSPITAL', 'ADMIN')")
    public List<DonorMatchResponse> matches(@PathVariable Long id, Authentication authentication) {
        return bloodRequestService.findMatches(id, authentication.getName(), isAdmin(authentication));
    }

    @PostMapping("/{id}/fulfill")
    @PreAuthorize("hasAnyRole('HOSPITAL', 'ADMIN')")
    public BloodRequest fulfill(@PathVariable Long id, Authentication authentication) {
        return bloodRequestService.markFulfilled(id, authentication.getName(), isAdmin(authentication));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        bloodRequestService.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
