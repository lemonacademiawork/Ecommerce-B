package com.lemonacademy.ecommerce.service;

import com.lemonacademy.ecommerce.dto.ChangePasswordRequest;
import com.lemonacademy.ecommerce.dto.UpdateProfileRequest;
import com.lemonacademy.ecommerce.dto.UserProfileResponse;
import com.lemonacademy.ecommerce.dto.UserResponse;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.exception.InvalidOperationException;
import com.lemonacademy.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (User) authentication.getPrincipal();
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile() {
        User user = getAuthenticatedUser();
        return convertToResponse(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(UpdateProfileRequest request) {
        User user = getAuthenticatedUser();
        
        // Fetch fresh copy from DB to make sure we're in persistent context
        User dbUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new InvalidOperationException("User not found"));

        dbUser.setName(request.getName());
        dbUser.setPhone(request.getPhone());
        User saved = userRepository.save(dbUser);
        
        return convertToResponse(saved);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = getAuthenticatedUser();

        User dbUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new InvalidOperationException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), dbUser.getPassword())) {
            throw new InvalidOperationException("Current password does not match");
        }

        dbUser.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(dbUser);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::convertToUserResponse)
                .toList();
    }

    private UserProfileResponse convertToResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .build();
    }

    private UserResponse convertToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
