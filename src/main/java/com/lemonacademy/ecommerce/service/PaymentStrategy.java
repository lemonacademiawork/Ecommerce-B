package com.lemonacademy.ecommerce.service;

import com.lemonacademy.ecommerce.entity.Order;
import org.springframework.web.multipart.MultipartFile;

public interface PaymentStrategy {
    Order processPayment(Order order, String transactionId, MultipartFile paymentScreenshot);
}
