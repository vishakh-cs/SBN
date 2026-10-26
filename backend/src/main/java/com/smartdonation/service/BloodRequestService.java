package com.smartdonation.service;

import com.smartdonation.dto.BloodRequestDtos.CreateBloodRequest;
import com.smartdonation.dto.DonorMatchResponse;
import com.smartdonation.dto.NearbyBloodRequestResponse;
import com.smartdonation.entity.BloodRequest;
import com.smartdonation.entity.RequestStatus;
import com.smartdonation.entity.UserAccount;
import com.smartdonation.matching.BloodCompatibility;
import com.smartdonation.matching.DistanceCalculator;
import com.smartdonation.exception.NotFoundException;
import org.springframework.security.access.AccessDeniedException;
import com.smartdonation.notification.NotificationService;
import com.smartdonation.repository.BloodRequestRepository;
import com.smartdonation.repository.UserAccountRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BloodRequestService {
    private final BloodRequestRepository requestRepository;
    private final MatchingService matchingService;
    private final NotificationService notificationService;
    private final UserAccountRepository userRepository;
    private final BloodCompatibility compatibility;
    private final DistanceCalculator distanceCalculator;
    private static final double NEARBY_RADIUS_KM = 50.0;

    public BloodRequestService(BloodRequestRepository requestRepository, MatchingService matchingService,
                               NotificationService notificationService, UserAccountRepository userRepository,
                               BloodCompatibility compatibility, DistanceCalculator distanceCalculator) {
        this.requestRepository = requestRepository;
        this.matchingService = matchingService;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.compatibility = compatibility;
        this.distanceCalculator = distanceCalculator;
    }

    public BloodRequest create(CreateBloodRequest payload, String authenticatedEmail) {
        BloodRequest request = new BloodRequest();
        request.setRequesterName(userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new NotFoundException("Authenticated user not found")).getFullName());
        request.setContactEmail(authenticatedEmail);
        request.setContactPhone(payload.contactPhone());
        request.setHospitalName(payload.hospitalName());
        request.setCity(payload.city());
        request.setLatitude(payload.latitude());
        request.setLongitude(payload.longitude());
        request.setRequiredBloodGroup(payload.requiredBloodGroup());
        request.setUnitsRequired(payload.unitsRequired());
        request.setUrgency(payload.urgency());
        request.setNotes(payload.notes());
        return requestRepository.save(request);
    }

    public List<DonorMatchResponse> matchAndNotify(Long requestId, String authenticatedEmail, boolean administrator) {
        BloodRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Blood request not found"));
        requireOwnerOrAdmin(request, authenticatedEmail, administrator);
        requireActive(request);
        List<DonorMatchResponse> matches = matchingService.findMatches(request);
        notificationService.notifyDonors(request, matches);
        request.setStatus(RequestStatus.NOTIFIED);
        requestRepository.save(request);
        return matches;
    }

    public List<DonorMatchResponse> findMatches(Long requestId, String authenticatedEmail, boolean administrator) {
        BloodRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Blood request not found"));
        requireOwnerOrAdmin(request, authenticatedEmail, administrator);
        requireActive(request);
        return matchingService.findMatches(request);
    }

    @Transactional
    public void delete(Long requestId, String authenticatedEmail) {
        BloodRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Blood request not found"));
        requireOwnerOrAdmin(request, authenticatedEmail, false);
        if (request.getStatus() == RequestStatus.FULFILLED) {
            throw new IllegalStateException("A fulfilled blood request cannot be deleted");
        }
        request.setStatus(RequestStatus.CANCELLED);
        requestRepository.save(request);
    }

    private void requireActive(BloodRequest request) {
        if (request.getStatus() != RequestStatus.OPEN && request.getStatus() != RequestStatus.NOTIFIED) {
            throw new IllegalStateException("Only active blood requests can be matched");
        }
    }

    public BloodRequest markFulfilled(Long requestId, String authenticatedEmail, boolean administrator) {
        BloodRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Blood request not found"));
        requireOwnerOrAdmin(request, authenticatedEmail, administrator);
        request.setStatus(RequestStatus.FULFILLED);
        return requestRepository.save(request);
    }

    public List<BloodRequest> list(String authenticatedEmail, boolean administrator) {
        return administrator ? requestRepository.findAll() : requestRepository.findByContactEmailIgnoreCase(authenticatedEmail);
    }

    public List<NearbyBloodRequestResponse> nearbyForDonor(String authenticatedEmail) {
        UserAccount donor = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new NotFoundException("Authenticated donor not found"));
        if (!donor.isBloodDonor() || donor.getBloodGroup() == null) {
            return List.of();
        }
        return requestRepository.findByStatusIn(List.of(RequestStatus.OPEN, RequestStatus.NOTIFIED)).stream()
                .filter(request -> compatibility.canDonate(donor.getBloodGroup(), request.getRequiredBloodGroup()))
                .map(request -> toNearbyResponse(donor, request))
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparingDouble(NearbyBloodRequestResponse::distanceKm)
                        .thenComparing(NearbyBloodRequestResponse::createdAt, java.util.Comparator.reverseOrder()))
                .limit(20)
                .toList();
    }

    private NearbyBloodRequestResponse toNearbyResponse(UserAccount donor, BloodRequest request) {
        double distance;
        boolean donorHasCoordinates = donor.getLatitude() != 0 || donor.getLongitude() != 0;
        boolean requestHasCoordinates = request.getLatitude() != 0 || request.getLongitude() != 0;
        if (donorHasCoordinates && requestHasCoordinates) {
            distance = distanceCalculator.distanceKm(donor.getLatitude(), donor.getLongitude(),
                    request.getLatitude(), request.getLongitude());
            if (distance > NEARBY_RADIUS_KM) return null;
        } else if (donor.getCity() != null && request.getCity() != null
                && donor.getCity().equalsIgnoreCase(request.getCity())) {
            distance = 0;
        } else {
            return null;
        }
        return new NearbyBloodRequestResponse(request.getId(), request.getRequiredBloodGroup(),
                request.getHospitalName(), request.getCity(), request.getUrgency(), request.getUnitsRequired(),
                request.getCreatedAt(), Math.round(distance * 10.0) / 10.0);
    }

    private void requireOwnerOrAdmin(BloodRequest request, String authenticatedEmail, boolean administrator) {
        if (!administrator && !request.getContactEmail().equalsIgnoreCase(authenticatedEmail)) {
            throw new AccessDeniedException("You may only access your own blood requests");
        }
    }
}
