package com.lemonacademy.ecommerce.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.lemonacademy.ecommerce.dto.AuthResponse;
import com.lemonacademy.ecommerce.dto.LoginRequest;
import com.lemonacademy.ecommerce.dto.RegisterRequest;
import com.lemonacademy.ecommerce.dto.TokenRequest;
import com.lemonacademy.ecommerce.entity.Role;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.exception.UserAlreadyExistsException;
import com.lemonacademy.ecommerce.repository.UserRepository;
import com.lemonacademy.ecommerce.security.JwtService;
import com.lemonacademy.ecommerce.dto.SendOtpRequest;
import com.lemonacademy.ecommerce.dto.VerifyOtpRequest;
import com.lemonacademy.ecommerce.exception.InvalidOperationException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class AuthService {

        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;
        private final AuthenticationManager authenticationManager;
        private final OtpService otpService;
        private final WhatsappService whatsappService;

        @Value("${google.client-id:}")
        private String googleClientId;

        @Transactional
        public User register(RegisterRequest request) {
                String email = (request.getEmail() != null && !request.getEmail().trim().isEmpty()) ? request.getEmail().trim() : null;
                String phone = (request.getPhone() != null && !request.getPhone().trim().isEmpty()) ? request.getPhone().trim() : null;

                // Validation: At least one of Email or Phone must be present
                if (email == null && phone == null) {
                        throw new InvalidOperationException("At least one of Email or Phone must be present.");
                }

                // If email is present:
                if (email != null) {
                        if (userRepository.existsByEmail(email)) {
                                throw new UserAlreadyExistsException("Email is already registered: " + email);
                        }
                        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
                                throw new InvalidOperationException("Password is required for email registration.");
                        }
                }

                // If phone is present:
                if (phone != null) {
                        if (userRepository.existsByPhone(phone)) {
                                throw new UserAlreadyExistsException("Phone number is already registered: " + phone);
                        }
                }

                boolean isPhoneOnly = (phone != null && email == null);

                User user = User.builder()
                                .name(request.getName())
                                .email(email)
                                .phone(phone)
                                .password(request.getPassword() != null ? passwordEncoder.encode(request.getPassword()) : null)
                                .role(Role.CUSTOMER)
                                .active(!isPhoneOnly) // Inactive only for phone-only registration until OTP verified
                                .build();

                user = userRepository.save(user);

                if (isPhoneOnly) {
                        // Generate OTP and send WhatsApp
                        String otp = otpService.generateAndStoreOtp(phone);
                        whatsappService.sendOtp(phone, otp);
                }

                return user;
        }

        public AuthResponse login(LoginRequest request) {
                String identifier = request.getIdentifier() != null ? request.getIdentifier().trim() : null;
                if (identifier == null || identifier.isEmpty()) {
                        throw new InvalidOperationException("Identifier must not be empty.");
                }

                boolean isEmail = identifier.contains("@");

                if (isEmail) {
                        // Email Login
                        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
                                throw new InvalidOperationException("Password is required for email login.");
                        }

                        authenticationManager.authenticate(
                                        new UsernamePasswordAuthenticationToken(
                                                        identifier,
                                                        request.getPassword()));

                        User user = userRepository.findByEmail(identifier)
                                        .orElseThrow(() -> new UsernameNotFoundException(
                                                        "User not found with email: " + identifier));

                        String jwtToken = jwtService.generateToken(user);

                        return AuthResponse.builder()
                                        .token(jwtToken)
                                        .role(user.getRole().name())
                                        .email(user.getEmail())
                                        .name(user.getName())
                                        .build();
                } else {
                        // Phone Login (Ignore password completely)
                        User user = userRepository.findByPhone(identifier)
                                        .orElseThrow(() -> new UsernameNotFoundException(
                                                        "User not found with phone: " + identifier));

                        // Generate OTP and store in Redis
                        String otp = otpService.generateAndStoreOtp(identifier);

                        // Send WhatsApp OTP
                        whatsappService.sendOtp(identifier, otp);

                        // Return response with null token (only generated after successful OTP verification)
                        return AuthResponse.builder()
                                        .token(null)
                                        .role(user.getRole().name())
                                        .email(user.getEmail())
                                        .name(user.getName())
                                        .build();
                }
        }

        public void sendOtp(SendOtpRequest request) {
                String phone = request.getPhone() != null ? request.getPhone().trim() : null;
                if (phone == null || phone.isEmpty()) {
                        throw new InvalidOperationException("Phone number must not be empty.");
                }
                if (!userRepository.existsByPhone(phone)) {
                        throw new UsernameNotFoundException("User not found with phone: " + phone);
                }

                String otp = otpService.generateAndStoreOtp(phone);
                whatsappService.sendOtp(phone, otp);
        }

        public AuthResponse verifyOtpLogin(VerifyOtpRequest request) {
                String phone = request.getPhone() != null ? request.getPhone().trim() : null;
                if (phone == null || phone.isEmpty()) {
                        throw new InvalidOperationException("Phone number must not be empty.");
                }
                otpService.verifyAndDeleteOtp(phone, request.getOtp());

                User user = userRepository.findByPhone(phone)
                                .orElseThrow(() -> new UsernameNotFoundException(
                                                "User not found with phone: " + phone));

                // Activate account if registration/login verification succeeds
                if (!user.isActive()) {
                        user.setActive(true);
                        user = userRepository.save(user);
                }

                String jwtToken = jwtService.generateToken(user);

                return AuthResponse.builder()
                                .token(jwtToken)
                                .role(user.getRole().name())
                                .email(user.getEmail())
                                .name(user.getName())
                                .build();
        }

        @Transactional
        public AuthResponse loginWithGoogle(TokenRequest request) {
                try {
                        NetHttpTransport transport = new NetHttpTransport();
                        GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

                        GoogleIdTokenVerifier.Builder verifierBuilder = new GoogleIdTokenVerifier.Builder(transport, jsonFactory);

                        if (googleClientId != null && !googleClientId.trim().isEmpty() && !googleClientId.contains("your-google-client-id")) {
                                verifierBuilder.setAudience(Collections.singletonList(googleClientId));
                        }

                        GoogleIdTokenVerifier verifier = verifierBuilder.build();
                        GoogleIdToken idToken = verifier.verify(request.getIdToken());

                        if (idToken == null) {
                                throw new IllegalArgumentException("Invalid Google ID Token");
                        }

                        GoogleIdToken.Payload payload = idToken.getPayload();
                        String email = payload.getEmail();
                        String rawName = (String) payload.get("name");
                        final String displayName = rawName != null ? rawName : email.split("@")[0];

                        User user = userRepository.findByEmail(email)
                                        .orElseGet(() -> {
                                                 User newUser = User.builder()
                                                                 .name(displayName)
                                                                 .email(email)
                                                                 .role(Role.CUSTOMER)
                                                                 .build();
                                                 return userRepository.save(newUser);
                                         });

                        String jwtToken = jwtService.generateToken(user);

                        return AuthResponse.builder()
                                        .token(jwtToken)
                                        .role(user.getRole().name())
                                        .email(user.getEmail())
                                        .name(user.getName())
                                        .build();

                } catch (Exception e) {
                        throw new RuntimeException("Google authentication failed: " + e.getMessage(), e);
                }
        }
}
