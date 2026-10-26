package com.smartdonation.controller;

import com.smartdonation.dto.AdminDashboardResponse;
import com.smartdonation.service.AdminService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDashboardResponse dashboard() {
        return adminService.dashboard();
    }
}
