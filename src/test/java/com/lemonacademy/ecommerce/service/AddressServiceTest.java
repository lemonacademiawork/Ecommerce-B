package com.lemonacademy.ecommerce.service;

import com.lemonacademy.ecommerce.dto.AddressRequest;
import com.lemonacademy.ecommerce.dto.AddressResponse;
import com.lemonacademy.ecommerce.entity.Address;
import com.lemonacademy.ecommerce.entity.Role;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.exception.ResourceNotFoundException;
import com.lemonacademy.ecommerce.exception.UnauthorizedAccessException;
import com.lemonacademy.ecommerce.repository.AddressRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock
    private AddressRepository addressRepository;

    @InjectMocks
    private AddressService addressService;

    private User user;
    private Address address;
    private AddressRequest request;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .email("test@example.com")
                .role(Role.CUSTOMER)
                .build();

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        address = Address.builder()
                .id(1L)
                .user(user)
                .fullName("John Doe")
                .addressLine1("123 Main St")
                .city("City")
                .isDefault(true)
                .build();

        request = new AddressRequest();
        request.setFullName("John Doe");
        request.setAddressLine1("123 Main St");
        request.setCity("City");
        request.setIsDefault(true);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void addAddress_FirstAddress_MadeDefault() {
        request.setIsDefault(false); // Should be made default anyway because it's the first

        when(addressRepository.findByUser(user)).thenReturn(Collections.emptyList());
        when(addressRepository.save(any(Address.class))).thenAnswer(i -> i.getArguments()[0]);

        AddressResponse response = addressService.addAddress(request);

        assertThat(response.getIsDefault()).isTrue();
        verify(addressRepository, times(1)).save(any(Address.class));
    }

    @Test
    void addAddress_NotFirst_RequestDefault() {
        Address existingAddress = Address.builder().id(2L).isDefault(true).build();
        List<Address> existingAddresses = new ArrayList<>();
        existingAddresses.add(existingAddress);

        when(addressRepository.findByUser(user)).thenReturn(existingAddresses);
        when(addressRepository.save(any(Address.class))).thenAnswer(i -> i.getArguments()[0]);

        AddressResponse response = addressService.addAddress(request);

        assertThat(response.getIsDefault()).isTrue();
        assertThat(existingAddress.getIsDefault()).isFalse();
        verify(addressRepository, times(2)).save(any(Address.class)); // 1 for old, 1 for new
    }

    @Test
    void getAddresses_Success() {
        when(addressRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(Collections.singletonList(address));

        List<AddressResponse> responses = addressService.getAddresses();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getFullName()).isEqualTo("John Doe");
    }

    @Test
    void updateAddress_Success() {
        when(addressRepository.findById(1L)).thenReturn(Optional.of(address));
        when(addressRepository.save(any(Address.class))).thenAnswer(i -> i.getArguments()[0]);

        request.setCity("New City");
        AddressResponse response = addressService.updateAddress(1L, request);

        assertThat(response.getCity()).isEqualTo("New City");
    }

    @Test
    void updateAddress_ChangeDefault() {
        address.setIsDefault(false);
        request.setIsDefault(true);

        Address otherAddress = Address.builder().id(2L).isDefault(true).build();
        
        when(addressRepository.findById(1L)).thenReturn(Optional.of(address));
        when(addressRepository.findByUser(user)).thenReturn(Collections.singletonList(otherAddress));
        when(addressRepository.save(any(Address.class))).thenAnswer(i -> i.getArguments()[0]);

        AddressResponse response = addressService.updateAddress(1L, request);

        assertThat(response.getIsDefault()).isTrue();
        assertThat(otherAddress.getIsDefault()).isFalse();
    }

    @Test
    void updateAddress_NotFound() {
        when(addressRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> addressService.updateAddress(1L, request));
    }

    @Test
    void updateAddress_Unauthorized() {
        User otherUser = User.builder().id(2L).build();
        address.setUser(otherUser);
        
        when(addressRepository.findById(1L)).thenReturn(Optional.of(address));

        assertThrows(UnauthorizedAccessException.class, () -> addressService.updateAddress(1L, request));
    }

    @Test
    void deleteAddress_Success() {
        when(addressRepository.findById(1L)).thenReturn(Optional.of(address));
        when(addressRepository.findByUser(user)).thenReturn(Collections.emptyList());

        addressService.deleteAddress(1L);

        verify(addressRepository, times(1)).delete(address);
    }

    @Test
    void deleteAddress_WasDefault_AssignNewDefault() {
        Address remainingAddress = Address.builder().id(2L).isDefault(false).build();
        List<Address> remaining = new ArrayList<>();
        remaining.add(remainingAddress);

        when(addressRepository.findById(1L)).thenReturn(Optional.of(address));
        when(addressRepository.findByUser(user)).thenReturn(remaining);

        addressService.deleteAddress(1L);

        verify(addressRepository, times(1)).delete(address);
        assertThat(remainingAddress.getIsDefault()).isTrue();
        verify(addressRepository, times(1)).save(remainingAddress);
    }

    @Test
    void deleteAddress_NotFound() {
        when(addressRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> addressService.deleteAddress(1L));
    }

    @Test
    void setDefaultAddress_Success() {
        address.setIsDefault(false);
        Address oldDefault = Address.builder().id(2L).isDefault(true).build();

        when(addressRepository.findById(1L)).thenReturn(Optional.of(address));
        when(addressRepository.findByUser(user)).thenReturn(Collections.singletonList(oldDefault));
        when(addressRepository.save(any(Address.class))).thenAnswer(i -> i.getArguments()[0]);

        AddressResponse response = addressService.setDefaultAddress(1L);

        assertThat(response.getIsDefault()).isTrue();
        assertThat(oldDefault.getIsDefault()).isFalse();
    }
}
