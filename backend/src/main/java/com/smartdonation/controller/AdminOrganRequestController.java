package com.smartdonation.controller;

import com.smartdonation.dto.OrganRequestRejection;
import com.smartdonation.dto.OrganRequestResponse;
import com.smartdonation.service.OrganRequestService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/organ-requests")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrganRequestController {
    private final OrganRequestService organRequestService;

    public AdminOrganRequestController(OrganRequestService organRequestService) {
        this.organRequestService = organRequestService;
    }

    @GetMapping
    public List<OrganRequestResponse> listAll() {
        return organRequestService.allRequests();
    }

    @GetMapping("/verified")
    public List<OrganRequestResponse> listVerified() {
        return organRequestService.verifiedRequests();
    }

    @PostMapping("/{id}/verify")
    public OrganRequestResponse verify(@PathVariable Long id, Authentication authentication) {
        return organRequestService.verify(id, authentication.getName());
    }

    @PostMapping("/{id}/reject")
    public OrganRequestResponse reject(@PathVariable Long id, @RequestBody OrganRequestRejection request,
                                       Authentication authentication) {
        return organRequestService.reject(id, request == null ? null : request.reason(), authentication.getName());
    }
}
