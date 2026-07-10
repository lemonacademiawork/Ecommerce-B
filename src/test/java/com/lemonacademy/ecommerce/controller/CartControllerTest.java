package com.lemonacademy.ecommerce.controller;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemonacademy.ecommerce.dto.AddToCartRequest;
import com.lemonacademy.ecommerce.dto.CartResponse;
import com.lemonacademy.ecommerce.dto.UpdateCartItemRequest;
import com.lemonacademy.ecommerce.entity.Role;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.exception.InsufficientStockException;
import com.lemonacademy.ecommerce.exception.ResourceNotFoundException;
import com.lemonacademy.ecommerce.security.JwtAuthenticationFilter;
import com.lemonacademy.ecommerce.security.SecurityConfig;
import com.lemonacademy.ecommerce.exception.GlobalExceptionHandler;
import com.lemonacademy.ecommerce.security.AdminUserDetailsService;
import org.springframework.context.annotation.Import;
import com.lemonacademy.ecommerce.security.JwtService;
import com.lemonacademy.ecommerce.service.CartService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@WebMvcTest(CartController.class)
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CartService cartService;

    @MockBean
    private JwtService jwtService;

    @MockBean(name = "customUserDetailsService")
    private UserDetailsService userDetailsService;

    @MockBean
    private AdminUserDetailsService adminUserDetailsService;

    private User customerUser;
    private CartResponse cartResponse;

    @BeforeEach
    void setUp() {
        customerUser = User.builder().id(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542")).email("customer@test.com").role(Role.CUSTOMER).build();

        cartResponse = CartResponse.builder()
                .cartId(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))
                .items(Collections.emptyList())
                .totalAmount(BigDecimal.ZERO)
                .totalItems(0)
                .build();
    }

    @Test
    void addToCart_Authenticated_Success() throws Exception {
        AddToCartRequest request = new AddToCartRequest();
        request.setProductId(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));
        request.setQuantity(2);

        when(cartService.addToCart(any(AddToCartRequest.class))).thenReturn(cartResponse);

        mockMvc.perform(post("/api/cart/add")
                        .with(user(customerUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void addToCart_Unauthenticated_Returns403() throws Exception {
        AddToCartRequest request = new AddToCartRequest();
        request.setProductId(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));
        request.setQuantity(2);

        mockMvc.perform(post("/api/cart/add")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void addToCart_InsufficientStock_Returns400() throws Exception {
        AddToCartRequest request = new AddToCartRequest();
        request.setProductId(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));
        request.setQuantity(100);

        when(cartService.addToCart(any(AddToCartRequest.class)))
                .thenThrow(new InsufficientStockException("Insufficient stock. Available: 5, Requested: 100"));

        mockMvc.perform(post("/api/cart/add")
                        .with(user(customerUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCart_Authenticated_Success() throws Exception {
        when(cartService.getCart()).thenReturn(cartResponse);

        mockMvc.perform(get("/api/cart")
                        .with(user(customerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cartId").value(1));
    }

    @Test
    void getCart_Unauthenticated_Returns403() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateCartItem_Authenticated_Success() throws Exception {
        UpdateCartItemRequest request = new UpdateCartItemRequest();
        request.setCartItemId(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));
        request.setQuantity(5);

        when(cartService.updateCartItem(any(UpdateCartItemRequest.class))).thenReturn(cartResponse);

        mockMvc.perform(put("/api/cart/update")
                        .with(user(customerUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void removeCartItem_Authenticated_Success() throws Exception {
        when(cartService.removeCartItem(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(cartResponse);

        mockMvc.perform(delete("/api/cart/remove/23db3d7a-683b-372b-8036-95da3ae5c542")
                        .with(user(customerUser))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Item removed from cart"));
    }

    @Test
    void removeCartItem_NotFound_Returns404() throws Exception {
        when(cartService.removeCartItem(any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Cart item not found with id: 1"));

        mockMvc.perform(delete("/api/cart/remove/23db3d7a-683b-372b-8036-95da3ae5c542")
                        .with(user(customerUser))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void clearCart_Authenticated_Success() throws Exception {
        when(cartService.clearCart()).thenReturn(cartResponse);

        mockMvc.perform(delete("/api/cart/clear")
                        .with(user(customerUser))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cart cleared successfully"));
    }
}
