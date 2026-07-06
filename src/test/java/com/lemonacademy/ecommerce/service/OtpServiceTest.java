package com.lemonacademy.ecommerce.service;

import com.lemonacademy.ecommerce.exception.InvalidOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private OtpService otpService;

    private final String phone = "+1234567890";
    private final String otpPrefix = "OTP:";
    private final String cooldownPrefix = "OTP_COOLDOWN:";
    private final String countPrefix = "OTP_COUNT:";

    @BeforeEach
    void setUp() {
        // By default, do not stub valueOperations for all tests, we will stub it per test
        // or just stub it here if all tests use it.
    }

    @Test
    void generateAndStoreOtp_Success() {
        when(redisTemplate.hasKey(cooldownPrefix + phone)).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(countPrefix + phone)).thenReturn(null);
        when(valueOperations.increment(countPrefix + phone)).thenReturn(1L);

        String otp = otpService.generateAndStoreOtp(phone);

        assertThat(otp).isNotNull().hasSize(6).matches("\\d{6}");
        verify(valueOperations, times(1)).set(eq(otpPrefix + phone), anyString(), any(Duration.class));
        verify(valueOperations, times(1)).set(eq(cooldownPrefix + phone), eq("1"), any(Duration.class));
        verify(redisTemplate, times(1)).expire(eq(countPrefix + phone), any(Duration.class));
    }

    @Test
    void generateAndStoreOtp_CooldownActive() {
        when(redisTemplate.hasKey(cooldownPrefix + phone)).thenReturn(true);

        assertThrows(InvalidOperationException.class, () -> otpService.generateAndStoreOtp(phone));
    }

    @Test
    void generateAndStoreOtp_MaxRequestsExceeded() {
        when(redisTemplate.hasKey(cooldownPrefix + phone)).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(countPrefix + phone)).thenReturn("3"); // Max is 3

        assertThrows(InvalidOperationException.class, () -> otpService.generateAndStoreOtp(phone));
    }

    @Test
    void verifyAndDeleteOtp_Success() {
        String otp = "123456";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(otpPrefix + phone)).thenReturn(otp);

        otpService.verifyAndDeleteOtp(phone, otp);

        verify(redisTemplate, times(1)).delete(otpPrefix + phone);
    }

    @Test
    void verifyAndDeleteOtp_Expired() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(otpPrefix + phone)).thenReturn(null);

        assertThrows(InvalidOperationException.class, () -> otpService.verifyAndDeleteOtp(phone, "123456"));
    }

    @Test
    void verifyAndDeleteOtp_Invalid() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(otpPrefix + phone)).thenReturn("654321");

        assertThrows(InvalidOperationException.class, () -> otpService.verifyAndDeleteOtp(phone, "123456"));
    }
}
