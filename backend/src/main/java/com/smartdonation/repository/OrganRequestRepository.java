package com.smartdonation.repository;

import com.smartdonation.entity.OrganRequest;
import com.smartdonation.entity.OrganRequestStatus;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganRequestRepository extends JpaRepository<OrganRequest, Long> {
    List<OrganRequest> findByPatientEmailIgnoreCaseOrderBySubmittedAtDesc(String email);
    List<OrganRequest> findAllByOrderBySubmittedAtDesc();
    List<OrganRequest> findByStatusOrderBySubmittedAtDesc(OrganRequestStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from OrganRequest request where request.id = :id")
    Optional<OrganRequest> findByIdForUpdate(@Param("id") Long id);
}
