package com.lemonacademy.ecommerce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemonacademy.ecommerce.dto.AdminAuthResponse;
import com.lemonacademy.ecommerce.dto.AdminLoginRequest;
import com.lemonacademy.ecommerce.security.AdminUserDetailsService;
import com.lemonacademy.ecommerce.security.JwtAuthenticationFilter;
import com.lemonacademy.ecommerce.security.JwtService;
import com.lemonacademy.ecommerce.security.SecurityConfig;
import com.lemonacademy.ecommerce.service.AdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lemonacademy.ecommerce.exception.GlobalExceptionHandler;

@WebMvcTest(AdminController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminService adminService;

    @MockBean
    private JwtService jwtService;

    @MockBean(name = "customUserDetailsService")
    private UserDetailsService userDetailsService;

    @MockBean
    private AdminUserDetailsService adminUserDetailsService;

    @Test
    void login_Success() throws Exception {
        AdminLoginRequest request = AdminLoginRequest.builder()
                .email("admin@example.com")
                .password("Admin@123")
                .build();

        AdminAuthResponse authResponse = AdminAuthResponse.builder()
                .token("mock-jwt-token-admin")
                .role("ADMIN")
                .email("admin@example.com")
                .fullName("Default Admin")
                .build();

        when(adminService.login(any(AdminLoginRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/admin/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("mock-jwt-token-admin"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void login_WrongCredentials_Returns401() throws Exception {
        AdminLoginRequest request = AdminLoginRequest.builder()
                .email("admin@example.com")
                .password("WrongPassword")
                .build();

        when(adminService.login(any(AdminLoginRequest.class)))
                .thenThrow(new BadCredentialsException("Invalid password"));

        mockMvc.perform(post("/api/admin/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_ValidationFailure_EmailMissing() throws Exception {
        AdminLoginRequest request = AdminLoginRequest.builder()
                .password("Admin@123")
                .build();

        mockMvc.perform(post("/api/admin/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.data.email").exists());
    }
}
