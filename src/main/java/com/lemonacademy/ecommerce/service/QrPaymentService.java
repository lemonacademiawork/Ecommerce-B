package com.lemonacademy.ecommerce.service;

import com.lemonacademy.ecommerce.entity.Order;
import com.lemonacademy.ecommerce.entity.OrderStatus;
import com.lemonacademy.ecommerce.entity.PaymentMethod;
import com.lemonacademy.ecommerce.entity.PaymentStatus;
import com.lemonacademy.ecommerce.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class QrPaymentService implements PaymentStrategy {

    private final CloudinaryService cloudinaryService;
    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public Order processPayment(Order order, String transactionId, MultipartFile paymentScreenshot) {
        
        if (paymentScreenshot == null || paymentScreenshot.isEmpty()) {
            throw new IllegalArgumentException(
                    "Payment screenshot is required. Please upload a screenshot where the transaction ID is clearly visible.");
        }

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new IllegalStateException("Order is already paid.");
        }

        if (order.getPaymentStatus() == PaymentStatus.FAILED || order.getPaymentStatus() == PaymentStatus.REFUNDED) {
            throw new IllegalStateException("Cannot process payment for an order with status: " + order.getPaymentStatus());
        }

        try {
            if (paymentScreenshot != null && !paymentScreenshot.isEmpty()) {
                String screenshotUrl = cloudinaryService.uploadImage(paymentScreenshot, "payments");
                order.setPaymentScreenshotUrl(screenshotUrl);
                log.info("Uploaded payment screenshot for order {}: {}", order.getId(), screenshotUrl);
            }
        } catch (Exception e) {
            log.error("Failed to upload payment screenshot to Cloudinary", e);
            throw new RuntimeException("Failed to upload payment screenshot.", e);
        }

        order.setPaymentMethod(PaymentMethod.QR);
        order.setPaymentStatus(PaymentStatus.PENDING_VERIFICATION);
        order.setStatus(OrderStatus.PAYMENT_PENDING);
        order.setTransactionId(transactionId);

        return orderRepository.save(order);
    }
}
