package com.lemonacademy.ecommerce.controller;

import com.lemonacademy.ecommerce.dto.ApiResponse;
import com.lemonacademy.ecommerce.dto.AuthResponse;
import com.lemonacademy.ecommerce.dto.LoginRequest;
import com.lemonacademy.ecommerce.dto.RegisterRequest;
import com.lemonacademy.ecommerce.dto.SendOtpRequest;
import com.lemonacademy.ecommerce.dto.TokenRequest;
import com.lemonacademy.ecommerce.dto.VerifyOtpRequest;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and authorization APIs")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<ApiResponse<User>> register(@Valid @RequestBody RegisterRequest request) {
        User registeredUser = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("User registered successfully", registeredUser));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse authResponse = authService.login(request);
        return ResponseEntity
                .ok(ApiResponse.success("Login successful", authResponse));
    }

        @PostMapping("/google")
        @Operation(summary = "Login with Google OAuth ID token")
        public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(@Valid @RequestBody TokenRequest request) {
                AuthResponse authResponse = authService.loginWithGoogle(request);
                return ResponseEntity
                                .ok(ApiResponse.success("Google login successful", authResponse));
        }

        @PostMapping("/send-otp")
        @Operation(summary = "Send OTP to phone number")
        public ResponseEntity<ApiResponse<String>> sendOtp(@Valid @RequestBody SendOtpRequest request) {
                authService.sendOtp(request);
                return ResponseEntity.ok(ApiResponse.success("OTP sent successfully", null));
        }

        @PostMapping("/resend-otp")
        @Operation(summary = "Resend OTP to phone number")
        public ResponseEntity<ApiResponse<String>> resendOtp(@Valid @RequestBody SendOtpRequest request) {
                authService.sendOtp(request);
                return ResponseEntity.ok(ApiResponse.success("OTP resent successfully", null));
        }

        @PostMapping("/verify-otp")
        @Operation(summary = "Verify OTP and login")
        public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
                AuthResponse authResponse = authService.verifyOtpLogin(request);
                return ResponseEntity.ok(ApiResponse.success("Login successful", authResponse));
        }
}
