package com.lemonacademy.ecommerce.controller;

import com.lemonacademy.ecommerce.exception.GlobalExceptionHandler;
import com.lemonacademy.ecommerce.exception.InvalidOperationException;
import com.lemonacademy.ecommerce.service.RazorpayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RazorpayWebhookControllerTest {

    @Mock
    private RazorpayService razorpayService;

    @InjectMocks
    private RazorpayWebhookController webhookController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(webhookController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void testHandleRazorpayWebhook_Success() throws Exception {
        String payload = "{\"event\": \"payment.captured\"}";
        String signature = "valid_sig_12345";

        doNothing().when(razorpayService).processWebhook(eq(payload), eq(signature));

        mockMvc.perform(post("/api/webhooks/razorpay")
                        .header("X-Razorpay-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string("Webhook processed successfully"));

        verify(razorpayService, times(1)).processWebhook(payload, signature);
    }

    @Test
    void testHandleRazorpayWebhook_MissingSignature_ReturnsBadRequest() throws Exception {
        String payload = "{\"event\": \"payment.captured\"}";

        mockMvc.perform(post("/api/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Missing X-Razorpay-Signature header"));

        verify(razorpayService, never()).processWebhook(anyString(), anyString());
    }

    @Test
    void testHandleRazorpayWebhook_InvalidSignature_ReturnsBadRequest() throws Exception {
        String payload = "{\"event\": \"payment.captured\"}";
        String signature = "invalid_sig_12345";

        doThrow(new InvalidOperationException("Invalid webhook signature."))
                .when(razorpayService).processWebhook(eq(payload), eq(signature));

        mockMvc.perform(post("/api/webhooks/razorpay")
                        .header("X-Razorpay-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid webhook signature."));

        verify(razorpayService, times(1)).processWebhook(payload, signature);
    }
}
