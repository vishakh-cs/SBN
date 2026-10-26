package com.smartdonation.service;

import com.smartdonation.dto.OrganRequestCreate;
import com.smartdonation.dto.OrganRequestResponse;
import com.smartdonation.entity.OrganRequest;
import com.smartdonation.entity.OrganRequestStatus;
import com.smartdonation.entity.UserAccount;
import com.smartdonation.entity.UserRole;
import com.smartdonation.exception.NotFoundException;
import com.smartdonation.repository.OrganRequestRepository;
import com.smartdonation.repository.UserAccountRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OrganRequestService {
    private static final Map<String, String> ORGAN_TYPES = Map.of(
            "kidney", "Kidney", "liver", "Liver", "heart", "Heart", "lung", "Lung",
            "pancreas", "Pancreas", "cornea", "Cornea", "other", "Other");
    private static final Map<String, String> URGENCIES = Map.of(
            "routine", "Routine", "urgent", "Urgent", "emergency", "Emergency");

    private final OrganRequestRepository requestRepository;
    private final UserAccountRepository userRepository;

    public OrganRequestService(OrganRequestRepository requestRepository, UserAccountRepository userRepository) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public OrganRequestResponse create(OrganRequestCreate payload, String authenticatedEmail) {
        if (payload == null) throw new IllegalArgumentException("Organ request details are required");
        UserAccount patient = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new NotFoundException("Authenticated patient not found"));
        if (patient.getRole() != UserRole.PATIENT) {
            throw new AccessDeniedException("Only patients can submit organ requests");
        }

        String organType = allowedValue(payload.organType(), ORGAN_TYPES, "Organ type");
        String hospitalName = requiredText(payload.hospitalName(), "Hospital or transplant centre", 180);
        String urgency = allowedValue(payload.urgency(), URGENCIES, "Urgency");
        String notes = payload.notes() == null ? null : payload.notes().trim();
        if (notes != null && notes.length() > 2000) {
            throw new IllegalArgumentException("Notes must be 2000 characters or fewer");
        }

        OrganRequest request = new OrganRequest();
        request.setPatient(patient);
        request.setOrganType(organType);
        request.setHospitalName(hospitalName);
        request.setUrgency(urgency);
        request.setNotes(notes);
        request.setStatus(OrganRequestStatus.PENDING);
        return OrganRequestResponse.from(requestRepository.save(request));
    }

    @Transactional(readOnly = true)
    public List<OrganRequestResponse> myRequests(String authenticatedEmail) {
        return requestRepository.findByPatientEmailIgnoreCaseOrderBySubmittedAtDesc(authenticatedEmail)
                .stream().map(OrganRequestResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<OrganRequestResponse> allRequests() {
        return requestRepository.findAllByOrderBySubmittedAtDesc().stream()
                .map(OrganRequestResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<OrganRequestResponse> verifiedRequests() {
        return requestRepository.findByStatusOrderBySubmittedAtDesc(OrganRequestStatus.VERIFIED).stream()
                .map(OrganRequestResponse::from).toList();
    }

    @Transactional
    public OrganRequestResponse verify(Long id, String authenticatedAdminEmail) {
        OrganRequest request = pendingRequest(id);
        UserAccount admin = adminAccount(authenticatedAdminEmail);
        request.setStatus(OrganRequestStatus.VERIFIED);
        request.setReviewedBy(admin);
        request.setReviewedAt(LocalDateTime.now());
        request.setRejectionReason(null);
        return OrganRequestResponse.from(requestRepository.save(request));
    }

    @Transactional
    public OrganRequestResponse reject(Long id, String reason, String authenticatedAdminEmail) {
        String rejectionReason = requiredText(reason, "Rejection reason", 1000);
        OrganRequest request = pendingRequest(id);
        UserAccount admin = adminAccount(authenticatedAdminEmail);
        request.setStatus(OrganRequestStatus.REJECTED);
        request.setReviewedBy(admin);
        request.setReviewedAt(LocalDateTime.now());
        request.setRejectionReason(rejectionReason);
        return OrganRequestResponse.from(requestRepository.save(request));
    }

    private OrganRequest pendingRequest(Long id) {
        OrganRequest request = requestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Organ request not found"));
        if (request.getStatus() != OrganRequestStatus.PENDING) {
            throw new IllegalStateException("Only pending organ requests can be reviewed");
        }
        return request;
    }

    private UserAccount adminAccount(String email) {
        UserAccount admin = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Authenticated administrator not found"));
        if (admin.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Only administrators can review organ requests");
        }
        return admin;
    }

    private String requiredText(String value, String fieldName, int maxLength) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException(fieldName + " is required");
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be " + maxLength + " characters or fewer");
        }
        return trimmed;
    }

    private String allowedValue(String value, Map<String, String> allowedValues, String fieldName) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException(fieldName + " is required");
        String normalized = allowedValues.get(value.trim().toLowerCase(java.util.Locale.ROOT));
        if (normalized == null) throw new IllegalArgumentException("Choose a valid " + fieldName.toLowerCase(java.util.Locale.ROOT));
        return normalized;
    }
}
