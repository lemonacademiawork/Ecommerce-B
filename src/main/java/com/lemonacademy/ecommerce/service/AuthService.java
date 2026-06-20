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

        @Value("${google.client-id:}")
        private String googleClientId;

        @Transactional
        public User register(RegisterRequest request) {
                if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
                        if (userRepository.existsByEmail(request.getEmail())) {
                                throw new UserAlreadyExistsException("Email is already registered: " + request.getEmail());
                        }
                }

                User user = User.builder()
                                .name(request.getName())
                                .email(request.getEmail())
                                .phone(request.getPhone())
                                .password(passwordEncoder.encode(request.getPassword()))
                                .role(Role.CUSTOMER)
                                .build();

                return userRepository.save(user);
        }

        public AuthResponse login(LoginRequest request) {
                authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(
                                                request.getEmail(),
                                                request.getPassword()));

                User user = userRepository.findByEmail(request.getEmail())
                                .orElseThrow(() -> new UsernameNotFoundException(
                                                "User not found with email: " + request.getEmail()));

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
