package com.lemonacademy.ecommerce.controller;

import com.lemonacademy.ecommerce.exception.InvalidOperationException;
import com.lemonacademy.ecommerce.service.RazorpayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks/razorpay")
@RequiredArgsConstructor
@Tag(name = "Razorpay Webhook API", description = "Endpoints for handling asynchronous callbacks and events from Razorpay.")
@Slf4j
public class RazorpayWebhookController {

    private final RazorpayService razorpayService;

    @PostMapping
    @Operation(summary = "Handle Razorpay Webhook", description = "Receives and validates server-to-server webhook events from Razorpay.")
    public ResponseEntity<String> handleRazorpayWebhook(
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestBody String rawPayload) {

        log.info("Incoming Razorpay webhook request received");

        if (signature == null || signature.isBlank()) {
            log.warn("Razorpay webhook rejected: Missing X-Razorpay-Signature header");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing X-Razorpay-Signature header");
        }

        try {
            razorpayService.processWebhook(rawPayload, signature);
            return ResponseEntity.ok("Webhook processed successfully");
        } catch (InvalidOperationException e) {
            log.warn("Razorpay webhook validation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error processing Razorpay webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error processing webhook");
        }
    }
}
