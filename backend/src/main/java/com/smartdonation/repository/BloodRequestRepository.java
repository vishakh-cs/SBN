package com.smartdonation.repository;

import com.smartdonation.entity.BloodRequest;
import com.smartdonation.entity.RequestStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BloodRequestRepository extends JpaRepository<BloodRequest, Long> {
    long countByStatus(RequestStatus status);
    List<BloodRequest> findByContactEmailIgnoreCase(String contactEmail);
    List<BloodRequest> findByStatusIn(List<RequestStatus> statuses);
}
