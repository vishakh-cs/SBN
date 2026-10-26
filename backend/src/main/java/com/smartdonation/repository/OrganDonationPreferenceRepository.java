package com.smartdonation.repository;

import com.smartdonation.entity.OrganDonationPreference;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganDonationPreferenceRepository extends JpaRepository<OrganDonationPreference, Long> {
    Optional<OrganDonationPreference> findByDonorId(Long donorId);
    long countByVerifiedTrue();
}
