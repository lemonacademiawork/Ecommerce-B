package com.lemonacademy.ecommerce.config;

import com.lemonacademy.ecommerce.entity.Admin;
import com.lemonacademy.ecommerce.entity.Role;
import com.lemonacademy.ecommerce.entity.Coupon;
import com.lemonacademy.ecommerce.repository.AdminRepository;
import com.lemonacademy.ecommerce.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    private final AdminRepository adminRepository;
    private final CouponRepository couponRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        String adminEmail = System.getenv("ADMIN_EMAIL");
        String adminPassword = System.getenv("ADMIN_PASSWORD");

        if (adminEmail == null || adminEmail.trim().isEmpty() ||
            adminPassword == null || adminPassword.trim().isEmpty()) {
            throw new IllegalStateException("Required environment variables ADMIN_EMAIL and ADMIN_PASSWORD must be configured.");
        }

        // Migration: If legacy admin exists, delete it to force recreation with new credentials
        adminRepository.findByEmailIgnoreCase("admin@example.com")
                .ifPresent(legacyAdmin -> {
                    adminRepository.delete(legacyAdmin);
                });

        if (adminRepository.count() == 0) {
            Admin defaultAdmin = Admin.builder()
                    .fullName("Default Admin")
                    .email(adminEmail.trim())
                    .password(passwordEncoder.encode(adminPassword))
                    .role(Role.ADMIN)
                    .active(true)
                    .build();
            adminRepository.save(defaultAdmin);
        }

        if (couponRepository.count() == 0) {
            Coupon firstOrderCoupon = Coupon.builder()
                    .code("FIRST7")
                    .discountPercentage(java.math.BigDecimal.valueOf(7.0))
                    .active(true)
                    .build();
            couponRepository.save(firstOrderCoupon);
        }

        couponRepository.findByCode("LEMON20").ifPresent(couponRepository::delete);
        couponRepository.findByCode("lemon20").ifPresent(couponRepository::delete);
    }
}
