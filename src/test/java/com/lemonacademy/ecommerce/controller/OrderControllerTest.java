package com.lemonacademy.ecommerce.controller;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemonacademy.ecommerce.dto.OrderRequest;
import com.lemonacademy.ecommerce.dto.OrderResponse;
import com.lemonacademy.ecommerce.dto.PageResponseDto;
import com.lemonacademy.ecommerce.entity.OrderStatus;
import com.lemonacademy.ecommerce.entity.Role;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.exception.InvalidOperationException;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@WebMvcTest(OrderController.class)
class OrderControllerTest {

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

    private User customerUser;
    private OrderResponse orderResponse;
    private List<OrderResponse> listResponse;

    @BeforeEach
    void setUp() {
        customerUser = User.builder().id(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542")).email("customer@test.com").role(Role.CUSTOMER).build();

        orderResponse = OrderResponse.builder()
                .id("23db3d7a-683b-372b-8036-95da3ae5c542")
                .userId(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))
                .totalAmount(new BigDecimal("2000.00"))
                .status(OrderStatus.PENDING)
                .items(Collections.emptyList())
                .build();

        listResponse = Collections.singletonList(orderResponse);
    }

    @Test
    void createOrder_Authenticated_Success() throws Exception {
        OrderRequest request = new OrderRequest();
        request.setAddressId(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));

        when(orderService.createOrder(any(OrderRequest.class))).thenReturn(orderResponse);

        mockMvc.perform(post("/api/orders")
                        .with(user(customerUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void createOrder_EmptyCart_Returns400() throws Exception {
        OrderRequest request = new OrderRequest();
        request.setAddressId(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));

        when(orderService.createOrder(any(OrderRequest.class)))
                .thenThrow(new InvalidOperationException("Cart is empty"));

        mockMvc.perform(post("/api/orders")
                        .with(user(customerUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_Unauthenticated_Returns403() throws Exception {
        OrderRequest request = new OrderRequest();
        request.setAddressId(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));

        mockMvc.perform(post("/api/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMyOrders_Authenticated_Success() throws Exception {
        PageResponseDto<OrderResponse> pageResponse = PageResponseDto.<OrderResponse>builder()
                .content(listResponse)
                .pageNumber(0)
                .pageSize(10)
                .totalElements(1)
                .totalPages(1)
                .last(true)
                .build();
        when(orderService.getMyOrders(any(org.springframework.data.domain.Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/orders").with(user(customerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value("23db3d7a-683b-372b-8036-95da3ae5c542"))
                .andExpect(jsonPath("$.data.pageNumber").value(0))
                .andExpect(jsonPath("$.data.pageSize").value(10));
    }

    @Test
    void getMyOrders_Unauthenticated_Returns403() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getOrderDetails_Authenticated_Success() throws Exception {
        when(orderService.getOrderDetails("23db3d7a-683b-372b-8036-95da3ae5c542")).thenReturn(orderResponse);

        mockMvc.perform(get("/api/orders/23db3d7a-683b-372b-8036-95da3ae5c542").with(user(customerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("23db3d7a-683b-372b-8036-95da3ae5c542"));
    }

    @Test
    void getOrderDetails_NotFound_Returns404() throws Exception {
        when(orderService.getOrderDetails(any(String.class)))
                .thenThrow(new ResourceNotFoundException("Order not found with id: 23db3d7a-683b-372b-8036-95da3ae5c542"));

        mockMvc.perform(get("/api/orders/23db3d7a-683b-372b-8036-95da3ae5c542").with(user(customerUser)))
                .andExpect(status().isNotFound());
    }
}
