package com.lemonacademy.ecommerce.service;

import com.lemonacademy.ecommerce.exception.InvalidOperationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final UpstashRedisService redisTemplate;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final String OTP_PREFIX = "OTP:";
    private static final String OTP_COUNT_PREFIX = "OTP_COUNT:";
    private static final String OTP_COOLDOWN_PREFIX = "OTP_COOLDOWN:";

    private static final int MAX_OTP_REQUESTS = 3;
    private static final long OTP_TTL_MINUTES = 5;
    private static final long COOLDOWN_SECONDS = 60;

    public String generateAndStoreOtp(String phone) {
        checkRateLimits(phone);

        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1000000));
        
        // Save OTP
        redisTemplate.set(OTP_PREFIX + phone, otp, OTP_TTL_MINUTES * 60);
        
        // Update limits
        updateRateLimits(phone);
        
        log.info("OTP generated for phone: {}", phone); // Never log the actual OTP value
        return otp;
    }

    public void verifyAndDeleteOtp(String phone, String otp) {
        String key = OTP_PREFIX + phone;
        String storedOtp = redisTemplate.get(key);
        
        if (storedOtp == null) {
            throw new InvalidOperationException("OTP expired or not found");
        }
        
        if (!storedOtp.equals(otp)) {
            throw new InvalidOperationException("Invalid OTP");
        }
        
        // Delete OTP after successful verification
        redisTemplate.delete(key);
    }

    private void checkRateLimits(String phone) {
        String cooldownKey = OTP_COOLDOWN_PREFIX + phone;
        if (redisTemplate.hasKey(cooldownKey)) {
            throw new InvalidOperationException("Please wait before requesting another OTP");
        }

        String countKey = OTP_COUNT_PREFIX + phone;
        String countStr = redisTemplate.get(countKey);
        if (countStr != null && Integer.parseInt(countStr) >= MAX_OTP_REQUESTS) {
            throw new InvalidOperationException("Maximum OTP requests exceeded. Try again later.");
        }
    }

    private void updateRateLimits(String phone) {
        String countKey = OTP_COUNT_PREFIX + phone;
        String cooldownKey = OTP_COOLDOWN_PREFIX + phone;

        // Set cooldown
        redisTemplate.set(cooldownKey, "1", COOLDOWN_SECONDS);

        // Increment count
        Long count = redisTemplate.increment(countKey);
        if (count != null && count == 1) {
            redisTemplate.expire(countKey, OTP_TTL_MINUTES * 60);
        }
    }
}
