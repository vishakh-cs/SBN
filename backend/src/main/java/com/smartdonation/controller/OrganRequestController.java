package com.smartdonation.controller;

import com.smartdonation.dto.OrganRequestCreate;
import com.smartdonation.dto.OrganRequestResponse;
import com.smartdonation.service.OrganRequestService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/organ-requests")
public class OrganRequestController {
    private final OrganRequestService organRequestService;

    public OrganRequestController(OrganRequestService organRequestService) {
        this.organRequestService = organRequestService;
    }

    @PostMapping
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<OrganRequestResponse> create(@RequestBody OrganRequestCreate request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(organRequestService.create(request, authentication.getName()));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('PATIENT')")
    public List<OrganRequestResponse> mine(Authentication authentication) {
        return organRequestService.myRequests(authentication.getName());
    }
}
