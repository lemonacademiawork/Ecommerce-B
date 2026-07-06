package com.lemonacademy.ecommerce.controller;

import com.lemonacademy.ecommerce.dto.ApiResponse;
import com.lemonacademy.ecommerce.dto.AdminAuthResponse;
import com.lemonacademy.ecommerce.dto.AdminLoginRequest;
import com.lemonacademy.ecommerce.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Authentication", description = "Administrator authentication and management APIs")
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/auth/login")
    @Operation(summary = "Login as administrator")
    public ResponseEntity<ApiResponse<AdminAuthResponse>> login(@Valid @RequestBody AdminLoginRequest request) {
        AdminAuthResponse authResponse = adminService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Admin login successful", authResponse));
    }
}
