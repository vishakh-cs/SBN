package com.smartdonation.service;

import com.smartdonation.dto.DonationHistoryRequest;
import com.smartdonation.dto.DonationHistoryResponse;
import com.smartdonation.entity.DonationHistory;
import com.smartdonation.entity.UserAccount;
import com.smartdonation.exception.NotFoundException;
import com.smartdonation.repository.BloodRequestRepository;
import com.smartdonation.repository.DonationHistoryRepository;
import com.smartdonation.repository.UserAccountRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class DonationService {
    private final DonationHistoryRepository historyRepository;
    private final UserAccountRepository userRepository;
    private final BloodRequestRepository requestRepository;

    public DonationService(DonationHistoryRepository historyRepository, UserAccountRepository userRepository, BloodRequestRepository requestRepository) {
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.requestRepository = requestRepository;
    }

    public DonationHistoryResponse record(DonationHistoryRequest payload, String authenticatedEmail, boolean administrator) {
        UserAccount donor = userRepository.findById(payload.donorId())
                .orElseThrow(() -> new NotFoundException("Donor not found"));
        if (!administrator && !donor.getEmail().equalsIgnoreCase(authenticatedEmail)) {
            throw new AccessDeniedException("You may only record your own donation history");
        }
        DonationHistory history = new DonationHistory();
        history.setDonor(donor);
        if (payload.bloodRequestId() != null) {
            var request = requestRepository.findById(payload.bloodRequestId())
                    .orElseThrow(() -> new NotFoundException("Blood request not found"));
            if (!administrator && !request.getContactEmail().equalsIgnoreCase(authenticatedEmail)) {
                throw new AccessDeniedException("You may only associate your donation with your own blood request");
            }
            history.setBloodRequest(request);
        }
        LocalDate donatedOn = payload.donatedOn() == null ? LocalDate.now() : payload.donatedOn();
        history.setDonatedOn(donatedOn);
        history.setDonationType(payload.donationType());
        history.setLocation(payload.location());
        history.setNotes(payload.notes());
        donor.setLastDonationDate(donatedOn);
        userRepository.save(donor);
        return DonationHistoryResponse.from(historyRepository.save(history));
    }

    public List<DonationHistoryResponse> byDonor(Long donorId, String authenticatedEmail, boolean administrator) {
        UserAccount donor = userRepository.findById(donorId).orElseThrow(() -> new NotFoundException("Donor not found"));
        if (!administrator && !donor.getEmail().equalsIgnoreCase(authenticatedEmail)) {
            throw new AccessDeniedException("You may only access your own donation history");
        }
        return historyRepository.findByDonorIdOrderByDonatedOnDesc(donorId).stream()
                .map(DonationHistoryResponse::from).toList();
    }
}
