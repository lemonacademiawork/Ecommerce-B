package com.lemonacademy.ecommerce.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsappServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private WhatsappService whatsappService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(whatsappService, "apiUrl", "https://api.zoepact.com/send");
        ReflectionTestUtils.setField(whatsappService, "apiToken", "test-api-token");
        ReflectionTestUtils.setField(whatsappService, "phoneNumberId", "test-phone-id");
        ReflectionTestUtils.setField(whatsappService, "templateId", "test-template-id");
        // Inject the mock RestTemplate
        ReflectionTestUtils.setField(whatsappService, "restTemplate", restTemplate);
    }

    @Test
    void sendOtp_Success() {
        ResponseEntity<String> mockResponse = ResponseEntity.ok("success");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(mockResponse);

        // Should not throw any exception
        whatsappService.sendOtp("+1234567890", "123456");

        verify(restTemplate, times(1))
                .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void sendOtp_ApiFailure_ThrowsRuntimeException() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("Connection refused"));

        assertThrows(RuntimeException.class, () -> whatsappService.sendOtp("+1234567890", "123456"));
    }
}
