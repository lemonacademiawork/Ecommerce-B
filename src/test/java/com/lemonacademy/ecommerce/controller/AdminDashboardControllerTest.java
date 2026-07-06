package com.lemonacademy.ecommerce.controller;

import com.lemonacademy.ecommerce.dto.DashboardResponse;
import com.lemonacademy.ecommerce.entity.Role;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.security.JwtAuthenticationFilter;
import com.lemonacademy.ecommerce.security.SecurityConfig;
import com.lemonacademy.ecommerce.exception.GlobalExceptionHandler;
import com.lemonacademy.ecommerce.security.AdminUserDetailsService;
import org.springframework.context.annotation.Import;
import com.lemonacademy.ecommerce.security.JwtService;
import com.lemonacademy.ecommerce.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@WebMvcTest(AdminDashboardController.class)
class AdminDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private JwtService jwtService;

    @MockBean(name = "customUserDetailsService")
    private UserDetailsService userDetailsService;

    @MockBean
    private AdminUserDetailsService adminUserDetailsService;

    private User adminUser;
    private User customerUser;
    private DashboardResponse dashboardResponse;

    @BeforeEach
    void setUp() {
        adminUser = User.builder().id(1L).email("admin@test.com").role(Role.ADMIN).build();
        customerUser = User.builder().id(2L).email("customer@test.com").role(Role.CUSTOMER).build();

        dashboardResponse = DashboardResponse.builder()
                .totalUsers(100L)
                .totalProducts(50L)
                .totalOrders(200L)
                .pendingOrders(20L)
                .deliveredOrders(150L)
                .cancelledOrders(30L)
                .build();
    }

    @Test
    void getDashboard_AsAdmin_Success() throws Exception {
        when(dashboardService.getDashboardStatistics()).thenReturn(dashboardResponse);

        mockMvc.perform(get("/api/admin/dashboard").with(user(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalUsers").value(100))
                .andExpect(jsonPath("$.data.totalProducts").value(50))
                .andExpect(jsonPath("$.data.totalOrders").value(200));
    }

    @Test
    void getDashboard_AsCustomer_Returns403() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard").with(user(customerUser)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDashboard_Unauthenticated_Returns403() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isForbidden());
    }
}
