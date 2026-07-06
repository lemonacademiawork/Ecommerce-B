package com.lemonacademy.ecommerce.service;

import com.lemonacademy.ecommerce.exception.InvalidOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private UpstashRedisService redisTemplate;

    @InjectMocks
    private OtpService otpService;

    private final String phone = "+1234567890";
    private final String otpPrefix = "OTP:";
    private final String cooldownPrefix = "OTP_COOLDOWN:";
    private final String countPrefix = "OTP_COUNT:";
    
    private static final long OTP_TTL_MINUTES = 5;
    private static final long COOLDOWN_SECONDS = 60;

    @BeforeEach
    void setUp() {
    }

    @Test
    void generateAndStoreOtp_Success() {
        when(redisTemplate.hasKey(cooldownPrefix + phone)).thenReturn(false);
        when(redisTemplate.get(countPrefix + phone)).thenReturn(null);
        when(redisTemplate.increment(countPrefix + phone)).thenReturn(1L);

        String otp = otpService.generateAndStoreOtp(phone);

        assertThat(otp).isNotNull().hasSize(6).matches("\\d{6}");
        verify(redisTemplate, times(1)).set(eq(otpPrefix + phone), anyString(), eq(OTP_TTL_MINUTES * 60));
        verify(redisTemplate, times(1)).set(eq(cooldownPrefix + phone), eq("1"), eq(COOLDOWN_SECONDS));
        verify(redisTemplate, times(1)).expire(eq(countPrefix + phone), eq(OTP_TTL_MINUTES * 60));
    }

    @Test
    void generateAndStoreOtp_CooldownActive() {
        when(redisTemplate.hasKey(cooldownPrefix + phone)).thenReturn(true);

        assertThrows(InvalidOperationException.class, () -> otpService.generateAndStoreOtp(phone));
    }

    @Test
    void generateAndStoreOtp_MaxRequestsExceeded() {
        when(redisTemplate.hasKey(cooldownPrefix + phone)).thenReturn(false);
        when(redisTemplate.get(countPrefix + phone)).thenReturn("3"); // Max is 3

        assertThrows(InvalidOperationException.class, () -> otpService.generateAndStoreOtp(phone));
    }

    @Test
    void verifyAndDeleteOtp_Success() {
        String otp = "123456";
        when(redisTemplate.get(otpPrefix + phone)).thenReturn(otp);

        otpService.verifyAndDeleteOtp(phone, otp);

        verify(redisTemplate, times(1)).delete(otpPrefix + phone);
    }

    @Test
    void verifyAndDeleteOtp_Expired() {
        when(redisTemplate.get(otpPrefix + phone)).thenReturn(null);

        assertThrows(InvalidOperationException.class, () -> otpService.verifyAndDeleteOtp(phone, "123456"));
    }

    @Test
    void verifyAndDeleteOtp_Invalid() {
        when(redisTemplate.get(otpPrefix + phone)).thenReturn("654321");

        assertThrows(InvalidOperationException.class, () -> otpService.verifyAndDeleteOtp(phone, "123456"));
    }
}
