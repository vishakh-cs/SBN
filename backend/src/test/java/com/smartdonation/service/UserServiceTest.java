package com.smartdonation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.smartdonation.dto.AuthDtos.RegisterRequest;
import com.smartdonation.dto.UserResponse;
import com.smartdonation.entity.BloodGroup;
import com.smartdonation.entity.UserAccount;
import com.smartdonation.entity.UserRole;
import com.smartdonation.repository.UserAccountRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private UserAccountRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void registerPublicAccessRejectsAdminRole() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        assertThatThrownBy(() -> userService.register(new RegisterRequest(
                "Alice Smith",
                "alice@example.com",
                "password123",
                UserRole.ADMIN,
                BloodGroup.O_POSITIVE,
                "9876543210",
                "Coimbatore",
                11.0168,
                76.9558,
                true,
                false,
                true,
                LocalDate.of(2024, 2, 12)
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DONOR or PATIENT");
    }

    @Test
    void registerPublicAccessRejectsHospitalRole() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        assertThatThrownBy(() -> userService.register(new RegisterRequest(
                "Alice Smith", "alice@example.com", "password123", UserRole.HOSPITAL, BloodGroup.O_POSITIVE,
                "9876543210", "Coimbatore", 11.0168, 76.9558, true, false, true, LocalDate.now())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DONOR or PATIENT");
    }

    @Test
    void registerWithoutRoleDefaultsToDonor() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });
        UserResponse response = userService.register(new RegisterRequest(
                "Alice Smith", "ALICE@example.com", "password123", null, BloodGroup.O_POSITIVE,
                "9876543210", "Coimbatore", 11.0168, 76.9558, true, false, true, LocalDate.now()));
        assertThat(response.role()).isEqualTo(UserRole.DONOR);
        assertThat(response.email()).isEqualTo("alice@example.com");
    }

    @Test
    void registerRequiresPassword() {
        assertThatThrownBy(() -> userService.register(new RegisterRequest(
                "Alice Smith",
                "alice@example.com",
                "",
                UserRole.DONOR,
                BloodGroup.A_POSITIVE,
                "9876543210",
                "Coimbatore",
                11.0168,
                76.9558,
                true,
                false,
                true,
                LocalDate.now()))
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password");
    }
}
