package com.lemonacademy.ecommerce.config;

import com.lemonacademy.ecommerce.entity.Admin;
import com.lemonacademy.ecommerce.entity.Role;
import com.lemonacademy.ecommerce.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        if (adminRepository.count() == 0) {
            Admin defaultAdmin = Admin.builder()
                    .fullName("Default Admin")
                    .email("admin@example.com")
                    .password(passwordEncoder.encode("Admin@123"))
                    .role(Role.ADMIN)
                    .active(true)
                    .build();
            adminRepository.save(defaultAdmin);
        }
    }
}
