package com.lemonacademy.ecommerce.service;

import com.lemonacademy.ecommerce.dto.AddressRequest;
import com.lemonacademy.ecommerce.dto.AddressResponse;
import com.lemonacademy.ecommerce.entity.Address;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.exception.ResourceNotFoundException;
import com.lemonacademy.ecommerce.exception.UnauthorizedAccessException;
import com.lemonacademy.ecommerce.repository.AddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (User) authentication.getPrincipal();
    }

    @Transactional
    public AddressResponse addAddress(AddressRequest request) {
        User user = getAuthenticatedUser();
        List<Address> existing = addressRepository.findByUser(user);

        // If it's the first address, or the request asks for default, set isDefault = true
        boolean makeDefault = existing.isEmpty() || Boolean.TRUE.equals(request.getIsDefault());

        if (makeDefault) {
            // reset all other addresses
            existing.forEach(a -> {
                if (Boolean.TRUE.equals(a.getIsDefault())) {
                    a.setIsDefault(false);
                    addressRepository.save(a);
                }
            });
        }

        Address address = Address.builder()
                .user(user)
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .addressLine1(request.getAddressLine1())
                .addressLine2(request.getAddressLine2())
                .city(request.getCity())
                .state(request.getState())
                .pincode(request.getPincode())
                .isDefault(makeDefault)
                .build();

        Address saved = addressRepository.save(address);
        return convertToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses() {
        User user = getAuthenticatedUser();
        return addressRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public AddressResponse updateAddress(Long id, AddressRequest request) {
        User user = getAuthenticatedUser();
        Address address = addressRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found with id: " + id));

        if (!address.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedAccessException("You are not authorized to update this address");
        }

        if (Boolean.TRUE.equals(request.getIsDefault()) && !Boolean.TRUE.equals(address.getIsDefault())) {
            // Change default: reset other defaults
            List<Address> existing = addressRepository.findByUser(user);
            existing.forEach(a -> {
                if (Boolean.TRUE.equals(a.getIsDefault())) {
                    a.setIsDefault(false);
                    addressRepository.save(a);
                }
            });
            address.setIsDefault(true);
        } else if (Boolean.FALSE.equals(request.getIsDefault()) && Boolean.TRUE.equals(address.getIsDefault())) {
            // Cannot unset default if it is the only address, check other addresses
            List<Address> existing = addressRepository.findByUser(user);
            if (existing.size() > 1) {
                address.setIsDefault(false);
            }
        }

        address.setFullName(request.getFullName());
        address.setPhone(request.getPhone());
        address.setAddressLine1(request.getAddressLine1());
        address.setAddressLine2(request.getAddressLine2());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setPincode(request.getPincode());

        Address saved = addressRepository.save(address);
        return convertToResponse(saved);
    }

    @Transactional
    public void deleteAddress(Long id) {
        User user = getAuthenticatedUser();
        Address address = addressRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found with id: " + id));

        if (!address.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedAccessException("You are not authorized to delete this address");
        }

        boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());
        addressRepository.delete(address);

        // If the deleted address was default, set another address as default if list is not empty
        if (wasDefault) {
            List<Address> remaining = addressRepository.findByUser(user);
            if (!remaining.isEmpty()) {
                Address newDefault = remaining.get(0);
                newDefault.setIsDefault(true);
                addressRepository.save(newDefault);
            }
        }
    }

    @Transactional
    public AddressResponse setDefaultAddress(Long id) {
        User user = getAuthenticatedUser();
        Address address = addressRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found with id: " + id));

        if (!address.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedAccessException("You are not authorized to modify this address");
        }

        List<Address> existing = addressRepository.findByUser(user);
        existing.forEach(a -> {
            if (Boolean.TRUE.equals(a.getIsDefault())) {
                a.setIsDefault(false);
                addressRepository.save(a);
            }
        });

        address.setIsDefault(true);
        Address saved = addressRepository.save(address);
        return convertToResponse(saved);
    }

    private AddressResponse convertToResponse(Address address) {
        return AddressResponse.builder()
                .id(address.getId())
                .fullName(address.getFullName())
                .phone(address.getPhone())
                .addressLine1(address.getAddressLine1())
                .addressLine2(address.getAddressLine2())
                .city(address.getCity())
                .state(address.getState())
                .pincode(address.getPincode())
                .isDefault(address.getIsDefault())
                .createdAt(address.getCreatedAt())
                .build();
    }
}
