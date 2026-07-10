package com.lemonacademy.ecommerce.service;

import java.util.UUID;

import com.lemonacademy.ecommerce.dto.AdminAuthResponse;
import com.lemonacademy.ecommerce.dto.AdminLoginRequest;
import com.lemonacademy.ecommerce.entity.Admin;
import com.lemonacademy.ecommerce.entity.Role;
import com.lemonacademy.ecommerce.exception.ResourceNotFoundException;
import com.lemonacademy.ecommerce.exception.UnauthorizedAccessException;
import com.lemonacademy.ecommerce.repository.AdminRepository;
import com.lemonacademy.ecommerce.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AdminServiceImpl adminService;

    private Admin mockAdmin;

    @BeforeEach
    void setUp() {
        mockAdmin = Admin.builder()
                .id(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))
                .fullName("Test Admin")
                .email("admin@test.com")
                .password("encodedPassword")
                .role(Role.ADMIN)
                .active(true)
                .build();
    }

    @Test
    void login_Success() {
        AdminLoginRequest request = AdminLoginRequest.builder()
                .email("admin@test.com")
                .password("password")
                .build();

        when(adminRepository.findByEmailIgnoreCase("admin@test.com")).thenReturn(Optional.of(mockAdmin));
        when(passwordEncoder.matches("password", "encodedPassword")).thenReturn(true);
        when(jwtService.generateToken(mockAdmin)).thenReturn("mockJwtToken");

        AdminAuthResponse response = adminService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("mockJwtToken");
        assertThat(response.getRole()).isEqualTo("ADMIN");
        verify(adminRepository, times(1)).save(mockAdmin);
    }

    @Test
    void login_AdminNotFound_ThrowsException() {
        AdminLoginRequest request = AdminLoginRequest.builder()
                .email("notfound@test.com")
                .password("password")
                .build();

        when(adminRepository.findByEmailIgnoreCase("notfound@test.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> adminService.login(request));
        verify(adminRepository, never()).save(any());
    }

    @Test
    void login_InactiveAdmin_ThrowsException() {
        AdminLoginRequest request = AdminLoginRequest.builder()
                .email("admin@test.com")
                .password("password")
                .build();

        mockAdmin.setActive(false);
        when(adminRepository.findByEmailIgnoreCase("admin@test.com")).thenReturn(Optional.of(mockAdmin));

        assertThrows(UnauthorizedAccessException.class, () -> adminService.login(request));
        verify(adminRepository, never()).save(any());
    }

    @Test
    void login_WrongPassword_ThrowsException() {
        AdminLoginRequest request = AdminLoginRequest.builder()
                .email("admin@test.com")
                .password("wrong")
                .build();

        when(adminRepository.findByEmailIgnoreCase("admin@test.com")).thenReturn(Optional.of(mockAdmin));
        when(passwordEncoder.matches("wrong", "encodedPassword")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> adminService.login(request));
        verify(adminRepository, never()).save(any());
    }
}
