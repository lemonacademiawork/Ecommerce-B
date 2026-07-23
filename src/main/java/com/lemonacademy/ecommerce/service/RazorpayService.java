package com.lemonacademy.ecommerce.service;

import com.lemonacademy.ecommerce.dto.RazorpayOrderResponse;
import com.lemonacademy.ecommerce.dto.RazorpayVerifyRequest;
import com.lemonacademy.ecommerce.entity.Order;
import com.lemonacademy.ecommerce.entity.OrderStatus;
import com.lemonacademy.ecommerce.entity.PaymentStatus;
import com.lemonacademy.ecommerce.exception.InvalidOperationException;
import com.lemonacademy.ecommerce.exception.ResourceNotFoundException;
import com.lemonacademy.ecommerce.repository.OrderRepository;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RazorpayService {

    private final RazorpayClient razorpayClient;
    private final OrderRepository orderRepository;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    @Transactional
    public RazorpayOrderResponse createRazorpayOrder(String internalOrderId) {
        Order order = findOrder(internalOrderId);

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new InvalidOperationException("Order is already paid.");
        }

        try {
            // Amount must be in paise (multiply by 100)
            BigDecimal amountInPaise = order.getTotalAmount().multiply(new BigDecimal("100"));

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise.longValue());
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", order.getOrderNumber() != null ? order.getOrderNumber() : order.getId().toString());

            com.razorpay.Order razorpayOrder = razorpayClient.orders.create(orderRequest);

            String razorpayOrderId = razorpayOrder.get("id");
            order.setRazorpayOrderId(razorpayOrderId);
            orderRepository.save(order);

            return RazorpayOrderResponse.builder()
                    .razorpayOrderId(razorpayOrderId)
                    .amount(order.getTotalAmount())
                    .currency("INR")
                    .internalOrderId(internalOrderId)
                    .build();

        } catch (RazorpayException e) {
            log.error("Error creating Razorpay order for internal order {}: {}", internalOrderId, e.getMessage());
            throw new InvalidOperationException("Failed to initiate Razorpay payment: " + e.getMessage());
        }
    }

    @Transactional
    public void verifyPayment(RazorpayVerifyRequest request) {
        Order order = findOrder(request.getInternalOrderId());

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new InvalidOperationException("Order is already paid.");
        }

        if (order.getRazorpayOrderId() == null || !order.getRazorpayOrderId().equals(request.getRazorpayOrderId())) {
            throw new InvalidOperationException("Razorpay Order ID mismatch.");
        }

        try {
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", request.getRazorpayOrderId());
            options.put("razorpay_payment_id", request.getRazorpayPaymentId());
            options.put("razorpay_signature", request.getRazorpaySignature());

            boolean isValid = Utils.verifyPaymentSignature(options, keySecret);

            if (isValid) {
                order.setPaymentStatus(PaymentStatus.PAID);
                order.setStatus(OrderStatus.CONFIRMED);
                order.setTransactionId(request.getRazorpayPaymentId());
                order.setPaymentVerifiedAt(LocalDateTime.now());
                orderRepository.save(order);
                log.info("Successfully verified Razorpay payment for order {}", order.getId());
            } else {
                order.setPaymentStatus(PaymentStatus.FAILED);
                order.setStatus(OrderStatus.PAYMENT_FAILED);
                orderRepository.save(order);
                throw new InvalidOperationException("Payment signature verification failed.");
            }
        } catch (RazorpayException e) {
            log.error("Razorpay signature verification error: {}", e.getMessage());
            throw new InvalidOperationException("Error verifying payment signature: " + e.getMessage());
        }
    }

    private Order findOrder(String orderId) {
        Order order = orderRepository.findByOrderNumber(orderId).orElse(null);
        if (order == null) {
            order = orderRepository.findById(UUID.fromString(orderId))
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        }
        return order;
    }
}
