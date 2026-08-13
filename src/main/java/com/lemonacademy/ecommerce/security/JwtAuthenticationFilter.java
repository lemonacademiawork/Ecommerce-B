package com.lemonacademy.ecommerce.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final AdminUserDetailsService adminUserDetailsService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            @org.springframework.beans.factory.annotation.Qualifier("customUserDetailsService") UserDetailsService userDetailsService,
            AdminUserDetailsService adminUserDetailsService
    ) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.adminUserDetailsService = adminUserDetailsService;
    }

    @Override
    public void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        try {
            userEmail = jwtService.extractUsername(jwt);
            String role = jwtService.extractRole(jwt);
            log.info("JWT auth filter: email={}, role={}, uri={}", userEmail, role, request.getRequestURI());

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = null;
                if ("ADMIN".equals(role)) {
                    try {
                        userDetails = this.adminUserDetailsService.loadUserByUsername(userEmail);
                        log.info("Admin loaded from admins table: {}", userEmail);
                    } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
                        log.info("Admin not found in admins table, falling back to users table: {}", userEmail);
                        userDetails = this.userDetailsService.loadUserByUsername(userEmail);
                    }
                } else {
                    userDetails = this.userDetailsService.loadUserByUsername(userEmail);
                }

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.info("Authentication set for user: {}, authorities: {}", userEmail, userDetails.getAuthorities());
                } else {
                    log.warn("JWT token invalid for user: {}", userEmail);
                }
            }
        } catch (Exception e) {
            log.error("JWT authentication failed for URI {}: {} - {}", request.getRequestURI(), e.getClass().getSimpleName(), e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}

