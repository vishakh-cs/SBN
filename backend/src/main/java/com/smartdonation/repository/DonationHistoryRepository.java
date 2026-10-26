package com.smartdonation.repository;

import com.smartdonation.entity.DonationHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DonationHistoryRepository extends JpaRepository<DonationHistory, Long> {
    List<DonationHistory> findByDonorIdOrderByDonatedOnDesc(Long donorId);
}
