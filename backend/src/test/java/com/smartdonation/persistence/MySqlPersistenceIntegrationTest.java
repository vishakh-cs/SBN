package com.smartdonation.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdonation.SmartDonationApplication;
import com.smartdonation.entity.UserAccount;
import com.smartdonation.entity.UserRole;
import com.smartdonation.repository.UserAccountRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(properties = {
        "spring.datasource.url=${MYSQL_TEST_URL:jdbc:mysql://localhost:3306/smart_donation_test}",
        "spring.datasource.username=${MYSQL_TEST_USERNAME:root}",
        "spring.datasource.password=${MYSQL_TEST_PASSWORD:}",
        "spring.jpa.hibernate.ddl-auto=update",
        "APP_SESSION_COOKIE_SECURE=false"
})
@EnabledIfEnvironmentVariable(named = "MYSQL_TEST_URL", matches = "^jdbc:mysql://.+")
class MySqlPersistenceIntegrationTest {
    @Autowired private UserAccountRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void accountDataIsAvailableAfterApplicationContextRestart() {
        String email = "restart." + UUID.randomUUID() + "@test.local";
        UserAccount account = new UserAccount();
        account.setFullName("Persistence Check");
        account.setEmail(email);
        account.setPasswordHash(passwordEncoder.encode("test-password-123"));
        account.setRole(UserRole.PATIENT);
        userRepository.save(account);

        ConfigurableApplicationContext restartedApplication = new SpringApplicationBuilder(SmartDonationApplication.class)
                .profiles("dev")
                .web(WebApplicationType.SERVLET)
                .run(
                        "--server.port=0",
                        "--spring.datasource.url=" + System.getenv("MYSQL_TEST_URL"),
                        "--spring.datasource.username=" + System.getenv().getOrDefault("MYSQL_TEST_USERNAME", "root"),
                        "--spring.datasource.password=" + System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", ""),
                        "--spring.jpa.hibernate.ddl-auto=update",
                        "--spring.main.banner-mode=off",
                        "--APP_SESSION_COOKIE_SECURE=false");
        try {
            UserAccount reloaded = restartedApplication.getBean(UserAccountRepository.class)
                    .findByEmail(email).orElseThrow();
            assertThat(reloaded.getRole()).isEqualTo(UserRole.PATIENT);
            assertThat(reloaded.getPasswordHash()).startsWith("$2a$");
        } finally {
            restartedApplication.close();
        }
    }
}
