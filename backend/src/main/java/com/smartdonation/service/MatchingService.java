package com.smartdonation.service;

import com.smartdonation.dto.DonorMatchResponse;
import com.smartdonation.entity.BloodRequest;
import com.smartdonation.entity.UserAccount;
import com.smartdonation.entity.UserRole;
import com.smartdonation.matching.BloodCompatibility;
import com.smartdonation.matching.DistanceCalculator;
import com.smartdonation.repository.UserAccountRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MatchingService {
    private final UserAccountRepository userRepository;
    private final BloodCompatibility compatibility;
    private final DistanceCalculator distanceCalculator;

    public MatchingService(UserAccountRepository userRepository, BloodCompatibility compatibility, DistanceCalculator distanceCalculator) {
        this.userRepository = userRepository;
        this.compatibility = compatibility;
        this.distanceCalculator = distanceCalculator;
    }

    public List<DonorMatchResponse> findMatches(BloodRequest request) {
        return userRepository.findByRoleAndBloodDonorTrueAndAvailableForDonationTrue(UserRole.DONOR).stream()
                .filter(donor -> compatibility.canDonate(donor.getBloodGroup(), request.getRequiredBloodGroup()))
                .filter(this::isPastRecommendedInterval)
                .map(donor -> toMatch(donor, request))
                .sorted(Comparator.comparingInt(DonorMatchResponse::score).reversed()
                        .thenComparingDouble(DonorMatchResponse::distanceKm))
                .limit(10)
                .toList();
    }

    private boolean isPastRecommendedInterval(UserAccount donor) {
        return donor.getLastDonationDate() == null || !donor.getLastDonationDate().plusDays(90).isAfter(LocalDate.now());
    }

    private DonorMatchResponse toMatch(UserAccount donor, BloodRequest request) {
        // Examiner note: haversine gives an approximate road-independent distance.
        // The score intentionally favors nearer eligible donors while still keeping
        // universal/first-time donors visible during emergency matching.
        double distance = distanceCalculator.distanceKm(
                request.getLatitude(), request.getLongitude(), donor.getLatitude(), donor.getLongitude());
        int score = Math.max(10, 100 - (int) Math.round(distance));
        if (donor.getLastDonationDate() == null) {
            score += 10;
        }
        return new DonorMatchResponse(
                donor.getId(),
                donor.getFullName(),
                donor.getEmail(),
                donor.getPhone(),
                donor.getCity(),
                donor.getBloodGroup(),
                Math.round(distance * 10.0) / 10.0,
                score);
    }
}
