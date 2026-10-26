package com.smartdonation.service;

import com.smartdonation.dto.OrganPreferenceRequest;
import com.smartdonation.dto.OrganPreferenceResponse;
import com.smartdonation.entity.OrganDonationPreference;
import com.smartdonation.entity.UserAccount;
import com.smartdonation.exception.NotFoundException;
import com.smartdonation.repository.OrganDonationPreferenceRepository;
import com.smartdonation.repository.UserAccountRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class OrganDonationService {
    private final OrganDonationPreferenceRepository organRepository;
    private final UserAccountRepository userRepository;

    public OrganDonationService(OrganDonationPreferenceRepository organRepository, UserAccountRepository userRepository) {
        this.organRepository = organRepository;
        this.userRepository = userRepository;
    }

    public OrganPreferenceResponse savePreference(OrganPreferenceRequest payload, String authenticatedEmail, boolean administrator) {
        UserAccount donor;
        if (administrator) {
            donor = userRepository.findById(payload.donorId())
                    .orElseThrow(() -> new NotFoundException("Donor not found"));
        } else {
            donor = userRepository.findByEmail(authenticatedEmail)
                    .orElseThrow(() -> new NotFoundException("Authenticated donor not found"));
            if (payload.donorId() != null && !payload.donorId().equals(donor.getId())) {
                throw new AccessDeniedException("You may only update your own organ preference");
            }
        }
        OrganDonationPreference preference = organRepository.findByDonorId(donor.getId()).orElse(new OrganDonationPreference());
        preference.setDonor(donor);
        preference.setOrgans(payload.organs());
        preference.setConsentStatement(payload.consentStatement());
        donor.setOrganDonor(true);
        userRepository.save(donor);
        return OrganPreferenceResponse.from(organRepository.save(preference));
    }

    public Optional<OrganPreferenceResponse> findPreferenceForDonor(String authenticatedEmail) {
        UserAccount donor = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new NotFoundException("Authenticated donor not found"));
        return organRepository.findByDonorId(donor.getId()).map(OrganPreferenceResponse::from);
    }

    public OrganPreferenceResponse verify(Long id) {
        OrganDonationPreference preference = organRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Organ preference not found"));
        preference.setVerified(true);
        return OrganPreferenceResponse.from(organRepository.save(preference));
    }

    public List<OrganPreferenceResponse> list() {
        return organRepository.findAll().stream().map(OrganPreferenceResponse::from).toList();
    }
}
