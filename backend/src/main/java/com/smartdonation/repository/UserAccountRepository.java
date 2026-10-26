package com.smartdonation.repository;

import com.smartdonation.entity.UserAccount;
import com.smartdonation.entity.UserRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmail(String email);
    boolean existsByEmail(String email);
    List<UserAccount> findByBloodDonorTrueAndAvailableForDonationTrue();
    List<UserAccount> findByRoleAndBloodDonorTrueAndAvailableForDonationTrue(UserRole role);
    long countByRole(UserRole role);
    long countByBloodDonorTrue();
    long countByOrganDonorTrue();
}
