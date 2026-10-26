package com.smartdonation.service;

import com.smartdonation.dto.AdminDashboardResponse;
import com.smartdonation.entity.RequestStatus;
import com.smartdonation.repository.BloodRequestRepository;
import com.smartdonation.repository.OrganDonationPreferenceRepository;
import com.smartdonation.repository.UserAccountRepository;
import org.springframework.stereotype.Service;

@Service
public class AdminService {
    private final UserAccountRepository userRepository;
    private final BloodRequestRepository requestRepository;
    private final OrganDonationPreferenceRepository organRepository;

    public AdminService(UserAccountRepository userRepository, BloodRequestRepository requestRepository, OrganDonationPreferenceRepository organRepository) {
        this.userRepository = userRepository;
        this.requestRepository = requestRepository;
        this.organRepository = organRepository;
    }

    public AdminDashboardResponse dashboard() {
        return new AdminDashboardResponse(
                userRepository.count(),
                userRepository.countByBloodDonorTrue(),
                userRepository.countByOrganDonorTrue(),
                requestRepository.countByStatus(RequestStatus.OPEN) + requestRepository.countByStatus(RequestStatus.NOTIFIED),
                organRepository.countByVerifiedTrue());
    }
}
