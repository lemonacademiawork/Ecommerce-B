package com.lemonacademy.ecommerce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemonacademy.ecommerce.dto.ChangePasswordRequest;
import com.lemonacademy.ecommerce.dto.UpdateProfileRequest;
import com.lemonacademy.ecommerce.dto.UserProfileResponse;
import com.lemonacademy.ecommerce.entity.Role;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.exception.InvalidOperationException;
import com.lemonacademy.ecommerce.security.JwtAuthenticationFilter;
import com.lemonacademy.ecommerce.security.SecurityConfig;
import com.lemonacademy.ecommerce.exception.GlobalExceptionHandler;
import com.lemonacademy.ecommerce.security.AdminUserDetailsService;
import org.springframework.context.annotation.Import;
import com.lemonacademy.ecommerce.security.JwtService;
import com.lemonacademy.ecommerce.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtService jwtService;

    @MockBean(name = "customUserDetailsService")
    private UserDetailsService userDetailsService;

    @MockBean
    private AdminUserDetailsService adminUserDetailsService;

    private User customerUser;
    private UserProfileResponse profileResponse;

    @BeforeEach
    void setUp() {
        customerUser = User.builder().id(1L).email("customer@test.com").role(Role.CUSTOMER).build();

        profileResponse = UserProfileResponse.builder()
                .id(1L)
                .name("Test User")
                .email("customer@test.com")
                .role(Role.CUSTOMER)
                .build();
    }

    @Test
    void getProfile_Authenticated_Success() throws Exception {
        when(userService.getProfile()).thenReturn(profileResponse);

        mockMvc.perform(get("/api/users/profile").with(user(customerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("customer@test.com"));
    }

    @Test
    void getProfile_Unauthenticated_Returns403() throws Exception {
        mockMvc.perform(get("/api/users/profile"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateProfile_Authenticated_Success() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setName("Updated Name");
        request.setPhone("+1234567890");

        UserProfileResponse updatedProfile = UserProfileResponse.builder()
                .id(1L)
                .name("Updated Name")
                .email("customer@test.com")
                .role(Role.CUSTOMER)
                .build();

        when(userService.updateProfile(any(UpdateProfileRequest.class))).thenReturn(updatedProfile);

        mockMvc.perform(put("/api/users/profile")
                        .with(user(customerUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Name"));
    }

    @Test
    void changePassword_Authenticated_Success() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("oldPassword");
        request.setNewPassword("newPassword");

        mockMvc.perform(put("/api/users/change-password")
                        .with(user(customerUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully"));
    }

    @Test
    void changePassword_WrongCurrentPassword_Returns400() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("wrongPassword");
        request.setNewPassword("newPassword");

        doThrow(new InvalidOperationException("Current password does not match"))
                .when(userService).changePassword(any(ChangePasswordRequest.class));

        mockMvc.perform(put("/api/users/change-password")
                        .with(user(customerUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
