package com.lemonacademy.ecommerce.controller;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemonacademy.ecommerce.dto.AddressResponse;
import com.lemonacademy.ecommerce.dto.AddressRequest;
import com.lemonacademy.ecommerce.entity.Role;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.exception.ResourceNotFoundException;
import com.lemonacademy.ecommerce.exception.UnauthorizedAccessException;
import com.lemonacademy.ecommerce.security.JwtAuthenticationFilter;
import com.lemonacademy.ecommerce.security.SecurityConfig;
import com.lemonacademy.ecommerce.exception.GlobalExceptionHandler;
import com.lemonacademy.ecommerce.security.AdminUserDetailsService;
import org.springframework.context.annotation.Import;
import com.lemonacademy.ecommerce.security.JwtService;
import com.lemonacademy.ecommerce.service.AddressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@WebMvcTest(AddressController.class)
class AddressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AddressService addressService;

    @MockBean
    private JwtService jwtService;

    @MockBean(name = "customUserDetailsService")
    private UserDetailsService userDetailsService;

    @MockBean
    private AdminUserDetailsService adminUserDetailsService;

    private User customerUser;
    private AddressResponse addressResponse;
    private AddressRequest addressRequest;

    @BeforeEach
    void setUp() {
        customerUser = User.builder().id(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542")).email("customer@test.com").role(Role.CUSTOMER).build();

        addressResponse = AddressResponse.builder()
                .id(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))
                .fullName("John Doe")
                .addressLine1("123 Main St")
                .city("TestCity")
                .isDefault(true)
                .build();

        addressRequest = new AddressRequest();
        addressRequest.setFullName("John Doe");
        addressRequest.setPhone("+1234567890");
        addressRequest.setAddressLine1("123 Main St");
        addressRequest.setCity("TestCity");
        addressRequest.setState("TestState");
        addressRequest.setPincode("123456");
    }

    @Test
    void addAddress_Authenticated_Success() throws Exception {
        when(addressService.addAddress(any(AddressRequest.class))).thenReturn(addressResponse);

        mockMvc.perform(post("/api/addresses")
                        .with(user(customerUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addressRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fullName").value("John Doe"));
    }

    @Test
    void addAddress_Unauthenticated_Returns403() throws Exception {
        mockMvc.perform(post("/api/addresses")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addressRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAddresses_Authenticated_Success() throws Exception {
        when(addressService.getAddresses()).thenReturn(Collections.singletonList(addressResponse));

        mockMvc.perform(get("/api/addresses").with(user(customerUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].fullName").value("John Doe"));
    }

    @Test
    void updateAddress_Authenticated_Success() throws Exception {
        when(addressService.updateAddress(any(UUID.class), any(AddressRequest.class))).thenReturn(addressResponse);

        mockMvc.perform(put("/api/addresses/23db3d7a-683b-372b-8036-95da3ae5c542")
                        .with(user(customerUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addressRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Address updated successfully"));
    }

    @Test
    void updateAddress_Unauthorized_Returns403() throws Exception {
        when(addressService.updateAddress(any(UUID.class), any(AddressRequest.class)))
                .thenThrow(new UnauthorizedAccessException("You are not authorized to update this address"));

        mockMvc.perform(put("/api/addresses/23db3d7a-683b-372b-8036-95da3ae5c542")
                        .with(user(customerUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addressRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteAddress_Authenticated_Success() throws Exception {
        doNothing().when(addressService).deleteAddress(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));

        mockMvc.perform(delete("/api/addresses/23db3d7a-683b-372b-8036-95da3ae5c542")
                        .with(user(customerUser))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Address deleted successfully"));
    }

    @Test
    void deleteAddress_NotFound_Returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Address not found with id: 1"))
                .when(addressService).deleteAddress(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));

        mockMvc.perform(delete("/api/addresses/23db3d7a-683b-372b-8036-95da3ae5c542")
                        .with(user(customerUser))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void setDefaultAddress_Authenticated_Success() throws Exception {
        when(addressService.setDefaultAddress(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(addressResponse);

        mockMvc.perform(put("/api/addresses/1/default")
                        .with(user(customerUser))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Default address updated successfully"));
    }
}
