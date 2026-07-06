package com.lemonacademy.ecommerce.service;

import com.lemonacademy.ecommerce.dto.AdminAuthResponse;
import com.lemonacademy.ecommerce.dto.AdminLoginRequest;
import com.lemonacademy.ecommerce.entity.Admin;
import com.lemonacademy.ecommerce.exception.ResourceNotFoundException;
import com.lemonacademy.ecommerce.exception.UnauthorizedAccessException;
import com.lemonacademy.ecommerce.repository.AdminRepository;
import com.lemonacademy.ecommerce.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    @Transactional
    public AdminAuthResponse login(AdminLoginRequest request) {
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";

        Admin admin = adminRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found with email: " + email));

        if (!admin.isActive()) {
            throw new UnauthorizedAccessException("Access denied. Admin account is disabled.");
        }

        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new BadCredentialsException("Invalid password");
        }

        admin.setLastLoginAt(LocalDateTime.now());
        adminRepository.save(admin);

        String token = jwtService.generateToken(admin);

        return AdminAuthResponse.builder()
                .token(token)
                .role(admin.getRole().name())
                .email(admin.getEmail())
                .fullName(admin.getFullName())
                .build();
    }
}
