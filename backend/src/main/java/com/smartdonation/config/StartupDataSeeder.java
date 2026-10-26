package com.smartdonation.config;

import com.smartdonation.entity.BloodGroup;
import com.smartdonation.entity.BloodRequest;
import com.smartdonation.entity.OrganDonationPreference;
import com.smartdonation.entity.UserAccount;
import com.smartdonation.entity.UserRole;
import com.smartdonation.repository.BloodRequestRepository;
import com.smartdonation.repository.OrganDonationPreferenceRepository;
import com.smartdonation.repository.UserAccountRepository;
import java.time.LocalDate;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.boot.CommandLineRunner;

@Component
@Profile("dev")
public class StartupDataSeeder implements CommandLineRunner {
    private final UserAccountRepository userRepository;
    private final BloodRequestRepository requestRepository;
    private final OrganDonationPreferenceRepository organRepository;
    private final PasswordEncoder passwordEncoder;

    public StartupDataSeeder(UserAccountRepository userRepository, BloodRequestRepository requestRepository,
                             OrganDonationPreferenceRepository organRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.requestRepository = requestRepository;
        this.organRepository = organRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }
        UserAccount priya = donor("Priya Raman", "priya.donor@test.local", BloodGroup.O_NEGATIVE, 13.0827, 80.2707, null);
        priya.setOrganDonor(true);
        UserAccount arun = donor("Arun Kumar", "arun.donor@test.local", BloodGroup.A_POSITIVE, 13.0674, 80.2376, LocalDate.now().minusDays(140));
        UserAccount meera = donor("Meera Shah", "meera.donor@test.local", BloodGroup.B_POSITIVE, 12.9716, 77.5946, LocalDate.now().minusDays(35));
        userRepository.save(priya);
        userRepository.save(arun);
        userRepository.save(meera);
        userRepository.save(account("Admin Officer", "admin@test.local", UserRole.ADMIN, BloodGroup.AB_POSITIVE));
        userRepository.save(account("City Hospital", "hospital@test.local", UserRole.HOSPITAL, BloodGroup.O_POSITIVE));
        userRepository.save(account("Ravi Patient", "patient@test.local", UserRole.PATIENT, BloodGroup.A_POSITIVE));

        OrganDonationPreference preference = new OrganDonationPreference();
        preference.setDonor(priya);
        preference.setOrgans("Kidney, liver, cornea");
        preference.setConsentStatement("I voluntarily consent to organ donation registration.");
        preference.setVerified(true);
        organRepository.save(preference);

        BloodRequest request = new BloodRequest();
        request.setRequesterName("City Hospital Emergency Desk");
        request.setContactEmail("hospital@test.local");
        request.setContactPhone("9000000000");
        request.setHospitalName("City Hospital");
        request.setCity("Chennai");
        request.setLatitude(13.0674);
        request.setLongitude(80.2376);
        request.setRequiredBloodGroup(BloodGroup.A_POSITIVE);
        request.setUnitsRequired(2);
        request.setUrgency("Critical");
        request.setNotes("Demo request seeded for viva walkthrough.");
        requestRepository.save(request);
    }

    private UserAccount donor(String name, String email, BloodGroup group, double latitude, double longitude, LocalDate lastDonationDate) {
        UserAccount user = new UserAccount();
        user.setFullName(name);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setRole(UserRole.DONOR);
        user.setBloodGroup(group);
        user.setPhone("9000000000");
        user.setCity("Chennai");
        user.setLatitude(latitude);
        user.setLongitude(longitude);
        user.setBloodDonor(true);
        user.setOrganDonor(false);
        user.setAvailableForDonation(true);
        user.setLastDonationDate(lastDonationDate);
        return user;
    }

    private UserAccount account(String name, String email, UserRole role, BloodGroup bloodGroup) {
        UserAccount user = new UserAccount();
        user.setFullName(name);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setRole(role);
        user.setBloodGroup(bloodGroup);
        user.setPhone("9000000000");
        user.setCity("Chennai");
        user.setLatitude(13.0674);
        user.setLongitude(80.2376);
        user.setBloodDonor(false);
        user.setOrganDonor(false);
        user.setAvailableForDonation(false);
        return user;
    }
}
