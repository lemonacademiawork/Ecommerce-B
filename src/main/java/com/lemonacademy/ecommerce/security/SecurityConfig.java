package com.lemonacademy.ecommerce.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    @Autowired(required = false)
    private OAuth2AuthenticationSuccessHandler oAuth2SuccessHandler;

    @Autowired(required = false)
    private OAuth2AuthenticationFailureHandler oAuth2FailureHandler;

    @Autowired(required = false)
    private HttpCookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthFilter,
            @org.springframework.beans.factory.annotation.Qualifier("customUserDetailsService") UserDetailsService userDetailsService
    ) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Wire the CORS configuration source defined below into Spring Security.
                // This ensures preflight (OPTIONS) requests are handled at the security layer
                // before any authentication filters run.
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(new Http403ForbiddenEntryPoint())
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/api/admin/auth/**",
                        "/api/products/**", "/api/categories/**",
                        "/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html",
                        "/oauth2/**", "/login/oauth2/**", "/api/webhooks/icarry/**", "/images/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/users/**", "/api/user/**").hasRole("CUSTOMER")
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> {
                    if (cookieAuthorizationRequestRepository != null) {
                        oauth2.authorizationEndpoint(authorization -> 
                                authorization.authorizationRequestRepository(cookieAuthorizationRequestRepository)
                        );
                    }
                    if (oAuth2SuccessHandler != null) {
                        oauth2.successHandler(oAuth2SuccessHandler);
                    }
                    if (oAuth2FailureHandler != null) {
                        oauth2.failureHandler(oAuth2FailureHandler);
                    }
                })
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Global CORS configuration applied via Spring Security.
     *
     * - Production origins: lemonhousecraft.in (with and without www)
     * - Development origins: localhost:4200 (Angular) and localhost:3000 (React/Vite)
     * - Credentials enabled for JWT cookie/header auth (requires explicit origins, not "*")
     * - Authorization header exposed so the frontend can read it from responses
     * - maxAge = 3600s (1 hour) to reduce preflight request frequency
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allowed origins — explicit list required when credentials are enabled (no "*")
        configuration.setAllowedOrigins(List.of(
                "https://lemonhousecraft.in",
                "https://www.lemonhousecraft.in",
                "http://localhost:4200",
                "http://localhost:3000"
        ));

        // Allowed HTTP methods
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        // Allow all headers the frontend may send (Authorization, Content-Type, etc.)
        configuration.setAllowedHeaders(List.of("*"));

        // Expose Authorization header so frontend JS can read it from responses
        configuration.setExposedHeaders(List.of("Authorization"));

        // Enable credentials — required for JWT auth via Authorization header
        configuration.setAllowCredentials(true);

        // Cache preflight response for 1 hour to reduce OPTIONS request overhead
        configuration.setMaxAge(3600L);

        // Apply this CORS configuration to all URL patterns
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        org.springframework.boot.web.servlet.FilterRegistrationBean<JwtAuthenticationFilter> registration = 
                new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
