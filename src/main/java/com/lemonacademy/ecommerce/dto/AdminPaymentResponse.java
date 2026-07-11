package com.lemonacademy.ecommerce.dto;

import com.lemonacademy.ecommerce.entity.PaymentMethod;
import com.lemonacademy.ecommerce.entity.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class AdminPaymentResponse {
    private String orderId;
    private String orderNumber;
    private String customerEmail;
    private BigDecimal totalAmount;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private String transactionId;
    private String paymentScreenshotUrl;
    private LocalDateTime orderDate;
    private LocalDateTime paymentVerifiedAt;
}
