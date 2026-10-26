package com.smartdonation.config;

import com.smartdonation.entity.UserAccount;
import com.smartdonation.entity.UserRole;
import com.smartdonation.repository.UserAccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the single local administrator account for the simple local project setup. */
@Component
@Profile("local")
public class LocalAdminSeeder implements CommandLineRunner {
    private static final String ADMIN_EMAIL = "admin@gmail.com";
    private static final String ADMIN_PASSWORD = "admin123";

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public LocalAdminSeeder(UserAccountRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        UserAccount admin = userRepository.findByEmail(ADMIN_EMAIL).orElseGet(() -> {
            UserAccount account = new UserAccount();
            account.setEmail(ADMIN_EMAIL);
            account.setRole(UserRole.ADMIN);
            account.setFullName("Smart Donation Admin");
            account.setBloodDonor(false);
            account.setOrganDonor(false);
            account.setAvailableForDonation(false);
            return account;
        });

        if (admin.getRole() != UserRole.ADMIN) {
            throw new IllegalStateException("Local admin email is already registered to a non-admin account");
        }
        admin.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        userRepository.save(admin);
    }
}
