package com.lemonacademy.ecommerce.service;

import java.util.UUID;

import com.lemonacademy.ecommerce.dto.ChangePasswordRequest;
import com.lemonacademy.ecommerce.dto.UpdateProfileRequest;
import com.lemonacademy.ecommerce.dto.UserProfileResponse;
import com.lemonacademy.ecommerce.entity.Role;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.exception.InvalidOperationException;
import com.lemonacademy.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))
                .name("Test User")
                .email("test@example.com")
                .phone("+1234567890")
                .password("encodedPassword")
                .role(Role.CUSTOMER)
                .build();

        // Setup Security Context
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                mockUser, null, mockUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getProfile_Success() {
        UserProfileResponse profile = userService.getProfile();

        assertThat(profile).isNotNull();
        assertThat(profile.getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void updateProfile_Success() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setName("Updated Name");
        request.setPhone("+0987654321");

        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(mockUser));
        
        User updatedUser = User.builder()
                .id(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))
                .name("Updated Name")
                .email("test@example.com")
                .phone("+0987654321")
                .role(Role.CUSTOMER)
                .build();
                
        when(userRepository.save(any(User.class))).thenReturn(updatedUser);

        UserProfileResponse response = userService.updateProfile(request);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Updated Name");
        assertThat(response.getPhone()).isEqualTo("+0987654321");
        
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void updateProfile_UserNotFound() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setName("Updated Name");

        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThrows(InvalidOperationException.class, () -> userService.updateProfile(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changePassword_Success() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("currentPassword");
        request.setNewPassword("newPassword");

        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("currentPassword", "encodedPassword")).thenReturn(true);
        when(passwordEncoder.encode("newPassword")).thenReturn("newEncodedPassword");

        userService.changePassword(request);

        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void changePassword_WrongCurrentPassword() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("wrongPassword");
        request.setNewPassword("newPassword");

        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("wrongPassword", "encodedPassword")).thenReturn(false);

        assertThrows(InvalidOperationException.class, () -> userService.changePassword(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changePassword_UserNotFound() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("currentPassword");
        request.setNewPassword("newPassword");

        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThrows(InvalidOperationException.class, () -> userService.changePassword(request));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }
}
