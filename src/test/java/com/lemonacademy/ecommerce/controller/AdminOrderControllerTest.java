package com.lemonacademy.ecommerce.controller;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemonacademy.ecommerce.dto.OrderResponse;
import com.lemonacademy.ecommerce.dto.OrderStatusRequest;
import com.lemonacademy.ecommerce.dto.PageResponseDto;
import com.lemonacademy.ecommerce.entity.OrderStatus;
import com.lemonacademy.ecommerce.entity.Role;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.exception.ResourceNotFoundException;
import com.lemonacademy.ecommerce.security.JwtAuthenticationFilter;
import com.lemonacademy.ecommerce.security.SecurityConfig;
import com.lemonacademy.ecommerce.exception.GlobalExceptionHandler;
import com.lemonacademy.ecommerce.security.AdminUserDetailsService;
import org.springframework.context.annotation.Import;
import com.lemonacademy.ecommerce.security.JwtService;
import com.lemonacademy.ecommerce.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@WebMvcTest(AdminOrderController.class)
class AdminOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @MockBean
    private JwtService jwtService;

    @MockBean(name = "customUserDetailsService")
    private UserDetailsService userDetailsService;

    @MockBean
    private AdminUserDetailsService adminUserDetailsService;

    private User adminUser;
    private User customerUser;
    private OrderResponse orderResponse;
    private List<OrderResponse> listResponse;

    @BeforeEach
    void setUp() {
        adminUser = User.builder().id(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542")).email("admin@test.com").role(Role.ADMIN).build();
        customerUser = User.builder().id(UUID.fromString("df4382cf-73c7-35ab-965a-b690f63e0acf")).email("customer@test.com").role(Role.CUSTOMER).build();

        orderResponse = OrderResponse.builder()
                .id("23db3d7a-683b-372b-8036-95da3ae5c542")
                .userId(UUID.fromString("df4382cf-73c7-35ab-965a-b690f63e0acf"))
                .totalAmount(new BigDecimal("2000.00"))
                .status(OrderStatus.PENDING)
                .items(Collections.emptyList())
                .build();

        listResponse = Collections.singletonList(orderResponse);
    }

    @Test
    void getAllOrders_AsAdmin_Success() throws Exception {
        when(orderService.getAllOrders()).thenReturn(listResponse);

        mockMvc.perform(get("/api/admin/orders").with(user(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    void getAllOrders_AsCustomer_Returns403() throws Exception {
        mockMvc.perform(get("/api/admin/orders").with(user(customerUser)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllOrders_Unauthenticated_Returns403() throws Exception {
        mockMvc.perform(get("/api/admin/orders"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getOrderDetails_AsAdmin_Success() throws Exception {
        when(orderService.getOrderDetails("23db3d7a-683b-372b-8036-95da3ae5c542")).thenReturn(orderResponse);

        mockMvc.perform(get("/api/admin/orders/23db3d7a-683b-372b-8036-95da3ae5c542").with(user(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void getOrderDetails_NotFound_Returns404() throws Exception {
        when(orderService.getOrderDetails(any(String.class)))
                .thenThrow(new ResourceNotFoundException("Order not found with id: 99"));

        mockMvc.perform(get("/api/admin/orders/99").with(user(adminUser)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateOrderStatus_AsAdmin_Success() throws Exception {
        OrderStatusRequest request = new OrderStatusRequest();
        request.setStatus(OrderStatus.SHIPPED);

        OrderResponse updatedResponse = OrderResponse.builder()
                .id("23db3d7a-683b-372b-8036-95da3ae5c542")
                .status(OrderStatus.SHIPPED)
                .items(Collections.emptyList())
                .build();

        when(orderService.updateOrderStatus("1", OrderStatus.SHIPPED)).thenReturn(updatedResponse);

        mockMvc.perform(put("/api/admin/orders/1/status")
                        .with(user(adminUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SHIPPED"))
                .andExpect(jsonPath("$.message").value("Order status updated successfully"));
    }

    @Test
    void updateOrderStatus_AsCustomer_Returns403() throws Exception {
        OrderStatusRequest request = new OrderStatusRequest();
        request.setStatus(OrderStatus.SHIPPED);

        mockMvc.perform(put("/api/admin/orders/1/status")
                        .with(user(customerUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateOrderStatus_OrderNotFound_Returns404() throws Exception {
        OrderStatusRequest request = new OrderStatusRequest();
        request.setStatus(OrderStatus.SHIPPED);

        when(orderService.updateOrderStatus(anyString(), any(OrderStatus.class)))
                .thenThrow(new ResourceNotFoundException("Order not found with id: 99"));

        mockMvc.perform(put("/api/admin/orders/99/status")
                        .with(user(adminUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
}
