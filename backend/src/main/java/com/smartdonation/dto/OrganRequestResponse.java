package com.smartdonation.dto;

import com.smartdonation.entity.OrganRequest;
import com.smartdonation.entity.OrganRequestStatus;
import java.time.LocalDateTime;

public record OrganRequestResponse(
        Long id,
        Long patientId,
        String patientName,
        String patientEmail,
        String organType,
        String hospitalName,
        String urgency,
        String notes,
        LocalDateTime submittedAt,
        OrganRequestStatus status,
        String reviewedByName,
        LocalDateTime reviewedAt,
        String rejectionReason) {
    public static OrganRequestResponse from(OrganRequest request) {
        return new OrganRequestResponse(
                request.getId(),
                request.getPatient().getId(),
                request.getPatient().getFullName(),
                request.getPatient().getEmail(),
                request.getOrganType(),
                request.getHospitalName(),
                request.getUrgency(),
                request.getNotes(),
                request.getSubmittedAt(),
                request.getStatus(),
                request.getReviewedBy() == null ? null : request.getReviewedBy().getFullName(),
                request.getReviewedAt(),
                request.getRejectionReason());
    }
}
