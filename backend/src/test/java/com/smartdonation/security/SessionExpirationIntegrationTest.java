package com.smartdonation.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdonation.entity.UserAccount;
import com.smartdonation.entity.UserRole;
import com.smartdonation.repository.UserAccountRepository;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=${MYSQL_TEST_URL:jdbc:mysql://localhost:3306/smart_donation_test}",
        "spring.datasource.username=${MYSQL_TEST_USERNAME:root}",
        "spring.datasource.password=${MYSQL_TEST_PASSWORD:}",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "server.servlet.session.timeout=1m",
        "server.tomcat.background-processor-delay=1s",
        "APP_SESSION_COOKIE_SECURE=false"
})
@EnabledIfEnvironmentVariable(named = "MYSQL_TEST_URL", matches = "^jdbc:mysql://.+")
class SessionExpirationIntegrationTest {
    private static final String EMAIL = "session-expiry@test.local";
    private static final String PASSWORD = "test-password-123";

    @LocalServerPort private int port;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        UserAccount account = new UserAccount();
        account.setFullName("Session Expiry");
        account.setEmail(EMAIL);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account.setRole(UserRole.PATIENT);
        userRepository.save(account);
    }

    @Test
    void sessionExpiresAfterConfiguredIdleTimeout() throws Exception {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        String base = "http://localhost:" + port + "/api/auth";

        HttpResponse<String> csrfResponse = client.send(HttpRequest.newBuilder(URI.create(base + "/csrf")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String csrfToken = objectMapper.readTree(csrfResponse.body()).get("token").asText();
        HttpResponse<String> loginResponse = client.send(HttpRequest.newBuilder(URI.create(base + "/login"))
                        .header("Content-Type", "application/json")
                        .header("X-XSRF-TOKEN", csrfToken)
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(
                                java.util.Map.of("email", EMAIL, "password", PASSWORD))))
                        .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(loginResponse.statusCode()).isEqualTo(200);
        assertThat(loginResponse.headers().allValues("Set-Cookie").stream()
                .filter(cookie -> cookie.startsWith("JSESSIONID="))
                .anyMatch(cookie -> cookie.contains("HttpOnly") && cookie.contains("SameSite=Lax"))).isTrue();

        HttpResponse<String> authenticated = client.send(HttpRequest.newBuilder(URI.create(base + "/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(authenticated.statusCode()).isEqualTo(200);
        assertThat(cookies.getCookieStore().get(URI.create(base)).stream()
                .anyMatch(cookie -> cookie.getName().equals("JSESSIONID"))).isTrue();
        Thread.sleep(65_000L);
        HttpResponse<String> expired = client.send(HttpRequest.newBuilder(URI.create(base + "/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(expired.statusCode()).isEqualTo(401);
    }
}
