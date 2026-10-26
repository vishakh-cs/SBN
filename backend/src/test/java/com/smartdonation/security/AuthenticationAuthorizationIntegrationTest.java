package com.smartdonation.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdonation.entity.BloodGroup;
import com.smartdonation.entity.BloodRequest;
import com.smartdonation.entity.OrganDonationPreference;
import com.smartdonation.entity.OrganRequest;
import com.smartdonation.entity.RequestStatus;
import com.smartdonation.entity.UserAccount;
import com.smartdonation.entity.UserRole;
import com.smartdonation.repository.BloodRequestRepository;
import com.smartdonation.repository.DonationHistoryRepository;
import com.smartdonation.repository.OrganDonationPreferenceRepository;
import com.smartdonation.repository.OrganRequestRepository;
import com.smartdonation.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=${MYSQL_TEST_URL:jdbc:mysql://localhost:3306/smart_donation_test}",
        "spring.datasource.username=${MYSQL_TEST_USERNAME:root}",
        "spring.datasource.password=${MYSQL_TEST_PASSWORD:}",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "APP_SESSION_COOKIE_SECURE=false",
        "AUTH_LOGIN_MAX_ATTEMPTS=10"
})
@EnabledIfEnvironmentVariable(named = "MYSQL_TEST_URL", matches = "^jdbc:mysql://.+")
@AutoConfigureMockMvc
class AuthenticationAuthorizationIntegrationTest {
    private static final String PASSWORD = "test-password-123";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private BloodRequestRepository requestRepository;
    @Autowired private DonationHistoryRepository donationRepository;
    @Autowired private OrganDonationPreferenceRepository organRepository;
    @Autowired private OrganRequestRepository organRequestRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private LoginRateLimiter rateLimiter;

    private UserAccount admin;
    private UserAccount patient;
    private UserAccount otherPatient;
    private UserAccount donor;
    private UserAccount hospital;

    @BeforeEach
    void setUp() {
        requestRepository.deleteAll();
        donationRepository.deleteAll();
        organRequestRepository.deleteAll();
        organRepository.deleteAll();
        userRepository.deleteAll();
        admin = user("admin.security@test.local", UserRole.ADMIN);
        patient = user("patient.security@test.local", UserRole.PATIENT);
        otherPatient = user("other.patient.security@test.local", UserRole.PATIENT);
        donor = user("donor.security@test.local", UserRole.DONOR);
        hospital = user("hospital.security@test.local", UserRole.HOSPITAL);
        userRepository.save(admin);
        userRepository.save(patient);
        userRepository.save(otherPatient);
        userRepository.save(donor);
        userRepository.save(hospital);
        rateLimiter.recordSuccess("127.0.0.1");
    }

    @Test
    void anonymousProtectedEndpointReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserWithoutAdminPermissionGets403() throws Exception {
        MockHttpSession session = login(patient.getEmail());
        mockMvc.perform(get("/api/admin/dashboard").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void successfulLoginCreatesSessionAndMeReturnsSafeProfile() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(patient.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.role").value("PATIENT"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        org.assertj.core.api.Assertions.assertThat(session).isNotNull();
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString()).doesNotContain("passwordHash");
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(patient.getEmail()))
                .andExpect(jsonPath("$.role").value("PATIENT"));
    }

    @Test
    void loginRotatesAnExistingAnonymousSessionId() throws Exception {
        MockHttpSession anonymousSession = new MockHttpSession();
        String oldSessionId = anonymousSession.getId();
        MvcResult result = mockMvc.perform(post("/api/auth/login").session(anonymousSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(patient.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession authenticatedSession = (MockHttpSession) result.getRequest().getSession(false);
        org.assertj.core.api.Assertions.assertThat(authenticatedSession.getId()).isNotEqualTo(oldSessionId);
    }

    @Test
    void failedLoginReturns401WithoutCreatingSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(patient.getEmail(), "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andReturn();
        org.assertj.core.api.Assertions.assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void logoutInvalidatesServerSession() throws Exception {
        MockHttpSession session = login(patient.getEmail());
        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void repeatedFailedLoginsAreRateLimited() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            mockMvc.perform(post("/api/auth/login").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentials(patient.getEmail(), "wrong-password")))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(patient.getEmail(), "wrong-password")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void publicRegistrationCannotCreateAdminOrHospitalAccounts() throws Exception {
        for (String role : new String[]{"ADMIN", "HOSPITAL"}) {
            String email = role.toLowerCase() + ".registration@test.local";
            mockMvc.perform(post("/api/auth/register").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fullName\":\"Public User\",\"email\":\"" + email
                                    + "\",\"password\":\"safe-password\",\"role\":\"" + role + "\"}"))
                    .andExpect(status().isBadRequest());
            org.assertj.core.api.Assertions.assertThat(userRepository.existsByEmail(email)).isFalse();
        }
    }

    @Test
    void patientCannotMatchAnotherPatientsRequest() throws Exception {
        BloodRequest request = new BloodRequest();
        request.setRequesterName("Another patient");
        request.setContactEmail(otherPatient.getEmail());
        request.setRequiredBloodGroup(BloodGroup.A_POSITIVE);
        request.setStatus(RequestStatus.OPEN);
        request = requestRepository.save(request);

        MockHttpSession session = login(patient.getEmail());
        mockMvc.perform(post("/api/blood-requests/" + request.getId() + "/match")
                        .session(session).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void donorCannotReadAnotherDonorsDonationHistory() throws Exception {
        MockHttpSession session = login(donor.getEmail());
        mockMvc.perform(get("/api/donations/donor/" + otherPatient.getId()).session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void roleMatrixProtectsAdminDataAndSensitiveUserList() throws Exception {
        for (UserAccount account : new UserAccount[]{patient, donor, hospital}) {
            MockHttpSession session = login(account.getEmail());
            mockMvc.perform(get("/api/admin/dashboard").session(session)).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/users").session(session)).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/organs/preferences").session(session)).andExpect(status().isForbidden());
        }
        MockHttpSession adminSession = login(admin.getEmail());
        mockMvc.perform(get("/api/admin/dashboard").session(adminSession)).andExpect(status().isOk());
        MvcResult users = mockMvc.perform(get("/api/users").session(adminSession)).andExpect(status().isOk()).andReturn();
        org.assertj.core.api.Assertions.assertThat(users.getResponse().getContentAsString())
                .doesNotContain("passwordHash").doesNotContain("$2a$");
        mockMvc.perform(get("/api/organs/preferences").session(adminSession)).andExpect(status().isOk());
    }

    @Test
    void organPreferenceIsDonorOwnedAndVerificationIsAdminOnly() throws Exception {
        MockHttpSession donorSession = login(donor.getEmail());
        String preference = "{\"donorId\":" + donor.getId() + ",\"organs\":\"Kidney\",\"consentStatement\":\"I consent\"}";
        MvcResult created = mockMvc.perform(post("/api/organs/preferences").session(donorSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(preference))
                .andExpect(status().isOk()).andReturn();
        Long prefId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(post("/api/organs/preferences").session(donorSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"donorId\":" + otherPatient.getId() + ",\"organs\":\"Kidney\",\"consentStatement\":\"I consent\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/organs/preferences/" + prefId + "/verify").session(donorSession).with(csrf()))
                .andExpect(status().isForbidden());
        MockHttpSession patientSession = login(patient.getEmail());
        mockMvc.perform(post("/api/organs/preferences").session(patientSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(preference))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/organs/preferences/" + prefId + "/verify").session(login(admin.getEmail())).with(csrf()))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(created.getResponse().getContentAsString())
                .doesNotContain("passwordHash").doesNotContain("$2a$");
    }

    @Test
    void patientOrganRequestsAreOwnedAndPendingUntilAdminReview() throws Exception {
        MockHttpSession patientSession = login(patient.getEmail());
        String payload = "{\"patientId\":" + otherPatient.getId()
                + ",\"organType\":\"Kidney\",\"hospitalName\":\"Central Transplant Centre\","
                + "\"urgency\":\"Urgent\",\"notes\":\"Needs review\"}";
        MvcResult created = mockMvc.perform(post("/api/organ-requests").session(patientSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.patientId").value(patient.getId()))
                .andReturn();
        Long requestId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/organ-requests/mine").session(patientSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(requestId));
        mockMvc.perform(get("/api/organ-requests/mine").session(login(otherPatient.getEmail())))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("[]"));
        mockMvc.perform(get("/api/admin/organ-requests").session(patientSession)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/organ-requests/" + requestId + "/verify").session(patientSession).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/organ-requests").session(login(donor.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());

        MvcResult verified = mockMvc.perform(post("/api/admin/organ-requests/" + requestId + "/verify")
                        .session(login(admin.getEmail())).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.reviewedByName").value(admin.getFullName()))
                .andExpect(jsonPath("$.reviewedAt").exists()).andReturn();
        org.assertj.core.api.Assertions.assertThat(verified.getResponse().getContentAsString())
                .doesNotContain("passwordHash").doesNotContain("$2a$");
        mockMvc.perform(get("/api/admin/organ-requests/verified").session(login(admin.getEmail())))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("VERIFIED"));
        mockMvc.perform(post("/api/admin/organ-requests/" + requestId + "/verify")
                        .session(login(admin.getEmail())).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void administratorRejectionRequiresReasonAndIsRecorded() throws Exception {
        OrganRequest request = new OrganRequest();
        request.setPatient(patient);
        request.setOrganType("Liver");
        request.setHospitalName("Central Transplant Centre");
        request.setUrgency("Routine");
        request = organRequestRepository.save(request);
        Long requestId = request.getId();
        MockHttpSession adminSession = login(admin.getEmail());

        mockMvc.perform(post("/api/admin/organ-requests/" + requestId + "/reject").session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/organ-requests/" + requestId + "/reject").session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Incomplete documentation\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewedByName").value(admin.getFullName()))
                .andExpect(jsonPath("$.reviewedAt").exists())
                .andExpect(jsonPath("$.rejectionReason").value("Incomplete documentation"));
        mockMvc.perform(get("/api/admin/organ-requests/verified").session(adminSession))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("[]"));
        mockMvc.perform(get("/api/admin/organ-requests").session(login(donor.getEmail())))
                .andExpect(status().isForbidden());
    }

    @Test
    void bloodRequestRoleMatrixAndOwnershipAreEnforced() throws Exception {
        MockHttpSession patientSession = login(patient.getEmail());
        String body = "{\"requesterName\":\"forged\",\"contactEmail\":\"" + otherPatient.getEmail()
                + "\",\"contactPhone\":\"555\",\"hospitalName\":\"Test\",\"city\":\"Test\",\"latitude\":0,\"longitude\":0,\"requiredBloodGroup\":\"A_POSITIVE\",\"unitsRequired\":1,\"urgency\":\"Normal\",\"notes\":\"test\"}";
        MvcResult created = mockMvc.perform(post("/api/blood-requests").session(patientSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        var saved = objectMapper.readTree(created.getResponse().getContentAsString());
        Long ownId = saved.get("id").asLong();
        org.assertj.core.api.Assertions.assertThat(saved.get("contactEmail").asText()).isEqualTo(patient.getEmail());
        donor.setBloodDonor(true);
        userRepository.save(donor);
        UserAccount unavailableDonor = user("unavailable.donor@test.local", UserRole.DONOR);
        unavailableDonor.setBloodDonor(true);
        unavailableDonor.setAvailableForDonation(false);
        userRepository.save(unavailableDonor);
        mockMvc.perform(get("/api/blood-requests").session(login(otherPatient.getEmail())))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("[]"));
        mockMvc.perform(get("/api/blood-requests/" + ownId + "/matches").session(patientSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].donorId").value(donor.getId()));
        mockMvc.perform(get("/api/blood-requests/" + ownId + "/matches").session(login(otherPatient.getEmail())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/blood-requests/" + ownId + "/matches").session(login(donor.getEmail())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/blood-requests/" + ownId + "/match").session(login(otherPatient.getEmail())).with(csrf()))
                .andExpect(status().isForbidden());
        MockHttpSession donorSession = login(donor.getEmail());
        mockMvc.perform(get("/api/blood-requests").session(donorSession)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/blood-requests").session(donorSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/blood-requests/" + ownId + "/fulfill").session(patientSession).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/blood-requests/" + ownId + "/fulfill").session(login(hospital.getEmail())).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/blood-requests/" + ownId + "/fulfill").session(login(admin.getEmail())).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void patientCanDeleteOnlyOwnBloodRequestAndCancelledRequestsAreNotMatchable() throws Exception {
        BloodRequest request = ownedRequest(patient);
        Long id = request.getId();
        MockHttpSession patientSession = login(patient.getEmail());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/blood-requests/" + id)
                        .session(login(otherPatient.getEmail())).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/blood-requests/" + id)
                        .session(login(donor.getEmail())).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/blood-requests/" + id)
                        .session(patientSession).with(csrf()))
                .andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(requestRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(RequestStatus.CANCELLED);
        mockMvc.perform(get("/api/blood-requests/" + id + "/matches").session(patientSession))
                .andExpect(status().isConflict());
    }

    @Test
    void donorCanUpdateOwnProfileAndLastDonationDateOnly() throws Exception {
        MockHttpSession donorSession = login(donor.getEmail());
        mockMvc.perform(patch("/api/users/me/profile").session(donorSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Updated Donor\",\"bloodGroup\":\"O_POSITIVE\","
                                + "\"phone\":\"9876543210\",\"city\":\"Chennai\",\"lastDonationDate\":\"2025-06-01\","
                                + "\"role\":\"ADMIN\",\"email\":\"admin@test.local\",\"availableForDonation\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Donor"))
                .andExpect(jsonPath("$.lastDonationDate").value("2025-06-01"))
                .andExpect(jsonPath("$.role").value("DONOR"))
                .andExpect(jsonPath("$.email").value(donor.getEmail()))
                .andExpect(jsonPath("$.availableForDonation").value(true));

        mockMvc.perform(patch("/api/users/me/profile").session(login(patient.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Forged\",\"bloodGroup\":\"O_POSITIVE\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/users/me/profile").session(donorSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Updated Donor\",\"bloodGroup\":\"O_POSITIVE\",\"lastDonationDate\":\"2099-01-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void donationCreationCannotForgeDonorOrLinkForeignRequest() throws Exception {
        MockHttpSession donorSession = login(donor.getEmail());
        String foreignDonor = "{\"donorId\":" + otherPatient.getId() + ",\"donationType\":\"Blood\",\"location\":\"Test\"}";
        mockMvc.perform(post("/api/donations").session(donorSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(foreignDonor))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/donations").session(login(patient.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"donorId\":" + donor.getId() + ",\"donationType\":\"Blood\"}"))
                .andExpect(status().isForbidden());
        BloodRequest foreignRequest = new BloodRequest();
        foreignRequest.setRequesterName("Other");
        foreignRequest.setContactEmail(otherPatient.getEmail());
        foreignRequest.setRequiredBloodGroup(BloodGroup.A_POSITIVE);
        foreignRequest.setStatus(RequestStatus.OPEN);
        foreignRequest = requestRepository.save(foreignRequest);
        mockMvc.perform(post("/api/donations").session(donorSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"donorId\":" + donor.getId() + ",\"bloodRequestId\":" + foreignRequest.getId()
                                + ",\"donationType\":\"Blood\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfIsRequiredAndCredentialedCorsUsesExplicitOrigin() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(patient.getEmail(), PASSWORD)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/auth/csrf").header("Origin", "http://localhost:5501"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Access-Control-Allow-Origin", "http://localhost:5501"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Access-Control-Allow-Credentials", "true"));
        mockMvc.perform(get("/api/auth/csrf").header("Origin", "https://evil.example"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void concurrentLoginsCreateIndependentSessionsAndLogoutOnlyInvalidatesOne() throws Exception {
        MockHttpSession first = login(patient.getEmail());
        MockHttpSession second = login(patient.getEmail());
        org.assertj.core.api.Assertions.assertThat(first.getId()).isNotEqualTo(second.getId());
        mockMvc.perform(post("/api/auth/logout").session(first).with(csrf())).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/auth/me").session(first)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").session(second)).andExpect(status().isOk());
    }

    @Test
    void everyProtectedControllerRouteEnforcesItsDeclaredRoleForAllAccounts() throws Exception {
        java.util.Map<UserRole, MockHttpSession> sessions = new java.util.EnumMap<>(UserRole.class);
        sessions.put(UserRole.DONOR, login(donor.getEmail()));
        sessions.put(UserRole.PATIENT, login(patient.getEmail()));
        sessions.put(UserRole.HOSPITAL, login(hospital.getEmail()));
        sessions.put(UserRole.ADMIN, login(admin.getEmail()));

        BloodRequest patientRequest = ownedRequest(patient);
        BloodRequest hospitalRequest = ownedRequest(hospital);
        BloodRequest adminRequest = ownedRequest(admin);
        OrganDonationPreference preference = new OrganDonationPreference();
        preference.setDonor(donor);
        preference.setOrgans("Kidney");
        preference.setConsentStatement("I consent");
        preference = organRepository.save(preference);

        java.util.List<RouteCase> routes = java.util.List.of(
                new RouteCase("GET", "/api/auth/me", null, java.util.Set.of(UserRole.values())),
                new RouteCase("GET", "/api/admin/dashboard", null, java.util.Set.of(UserRole.ADMIN)),
                new RouteCase("GET", "/api/users", null, java.util.Set.of(UserRole.ADMIN)),
                new RouteCase("POST", "/api/blood-requests", bloodRequestJson(patient.getEmail()),
                        java.util.Set.of(UserRole.PATIENT, UserRole.HOSPITAL, UserRole.ADMIN)),
                new RouteCase("GET", "/api/blood-requests", null,
                        java.util.Set.of(UserRole.PATIENT, UserRole.HOSPITAL, UserRole.ADMIN)),
                new RouteCase("POST", "/api/blood-requests/" + patientRequest.getId() + "/match", null,
                        java.util.Set.of(UserRole.PATIENT, UserRole.ADMIN)),
                new RouteCase("POST", "/api/blood-requests/" + hospitalRequest.getId() + "/match", null,
                        java.util.Set.of(UserRole.HOSPITAL, UserRole.ADMIN)),
                new RouteCase("POST", "/api/blood-requests/" + adminRequest.getId() + "/fulfill", null,
                        java.util.Set.of(UserRole.ADMIN)),
                new RouteCase("POST", "/api/blood-requests/" + hospitalRequest.getId() + "/fulfill", null,
                        java.util.Set.of(UserRole.HOSPITAL, UserRole.ADMIN)),
                new RouteCase("POST", "/api/donations", "{\"donorId\":" + donor.getId() + ",\"donationType\":\"Blood\"}",
                        java.util.Set.of(UserRole.DONOR, UserRole.ADMIN)),
                new RouteCase("GET", "/api/donations/donor/" + donor.getId(), null,
                        java.util.Set.of(UserRole.DONOR, UserRole.ADMIN)),
                new RouteCase("POST", "/api/organs/preferences", "{\"donorId\":" + donor.getId()
                        + ",\"organs\":\"Kidney\",\"consentStatement\":\"I consent\"}",
                        java.util.Set.of(UserRole.DONOR, UserRole.ADMIN)),
                new RouteCase("GET", "/api/organs/preferences", null, java.util.Set.of(UserRole.ADMIN)),
                new RouteCase("POST", "/api/organs/preferences/" + preference.getId() + "/verify", null,
                        java.util.Set.of(UserRole.ADMIN)));

        for (RouteCase route : routes) {
            MockHttpServletRequestBuilder anonymousRequest = route.request(null);
            mockMvc.perform(anonymousRequest).andExpect(status().isUnauthorized());
            for (UserRole role : UserRole.values()) {
                int expected = route.allowed().contains(role) ? 200 : 403;
                mockMvc.perform(route.request(sessions.get(role))).andExpect(status().is(expected));
            }
        }
    }

    private BloodRequest ownedRequest(UserAccount owner) {
        BloodRequest request = new BloodRequest();
        request.setRequesterName(owner.getFullName());
        request.setContactEmail(owner.getEmail());
        request.setRequiredBloodGroup(BloodGroup.A_POSITIVE);
        request.setStatus(RequestStatus.OPEN);
        return requestRepository.save(request);
    }

    private String bloodRequestJson(String contactEmail) {
        return "{\"requesterName\":\"forged\",\"contactEmail\":\"" + contactEmail
                + "\",\"contactPhone\":\"555\",\"hospitalName\":\"Test\",\"city\":\"Test\",\"latitude\":0,\"longitude\":0,\"requiredBloodGroup\":\"A_POSITIVE\",\"unitsRequired\":1,\"urgency\":\"Normal\",\"notes\":\"matrix\"}";
    }

    private record RouteCase(String method, String path, String body, java.util.Set<UserRole> allowed) {
        MockHttpServletRequestBuilder request(MockHttpSession session) {
            MockHttpServletRequestBuilder builder = switch (method) {
                case "GET" -> get(path);
                case "POST" -> post(path).with(csrf());
                default -> throw new IllegalArgumentException("Unsupported test method");
            };
            if (session != null) builder.session(session);
            if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(body);
            return builder;
        }
    }

    private MockHttpSession login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String credentials(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", password));
    }

    private UserAccount user(String email, UserRole role) {
        UserAccount account = new UserAccount();
        account.setFullName(role.name() + " User");
        account.setEmail(email);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account.setRole(role);
        account.setBloodGroup(BloodGroup.A_POSITIVE);
        account.setCity("Test City");
        account.setLatitude(0);
        account.setLongitude(0);
        return account;
    }
}
