package com.smartdonation.repository;

import com.smartdonation.entity.DonorNotification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DonorNotificationRepository extends JpaRepository<DonorNotification, Long> {
    List<DonorNotification> findByBloodRequestId(Long bloodRequestId);
}
