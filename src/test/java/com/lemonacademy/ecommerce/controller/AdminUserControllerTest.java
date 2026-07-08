package com.lemonacademy.ecommerce.controller;

import com.lemonacademy.ecommerce.dto.UserResponse;
import com.lemonacademy.ecommerce.entity.Role;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.security.JwtAuthenticationFilter;
import com.lemonacademy.ecommerce.security.SecurityConfig;
import com.lemonacademy.ecommerce.exception.GlobalExceptionHandler;
import com.lemonacademy.ecommerce.security.AdminUserDetailsService;
import com.lemonacademy.ecommerce.security.JwtService;
import com.lemonacademy.ecommerce.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@WebMvcTest(AdminUserController.class)
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtService jwtService;

    @MockBean(name = "customUserDetailsService")
    private UserDetailsService userDetailsService;

    @MockBean
    private AdminUserDetailsService adminUserDetailsService;

    private User adminUser;
    private User customerUser;
    private List<UserResponse> mockUsers;

    @BeforeEach
    void setUp() {
        adminUser = User.builder().id(1L).email("admin@test.com").role(Role.ADMIN).build();
        customerUser = User.builder().id(2L).email("customer@test.com").role(Role.CUSTOMER).build();

        mockUsers = List.of(
                UserResponse.builder()
                        .id(2L)
                        .name("Customer User")
                        .email("customer@test.com")
                        .phone("1234567890")
                        .role(Role.CUSTOMER)
                        .active(true)
                        .createdAt(LocalDateTime.now())
                        .build()
        );
    }

    @Test
    void getAllUsers_AsAdmin_Success() throws Exception {
        when(userService.getAllUsers()).thenReturn(mockUsers);

        mockMvc.perform(get("/api/admin/users").with(user(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(2))
                .andExpect(jsonPath("$.data[0].name").value("Customer User"))
                .andExpect(jsonPath("$.data[0].email").value("customer@test.com"))
                .andExpect(jsonPath("$.data[0].role").value("CUSTOMER"))
                .andExpect(jsonPath("$.data[0].active").value(true));
    }

    @Test
    void getAllUsers_AsCustomer_Returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users").with(user(customerUser)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllUsers_Unauthenticated_Returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }
}
