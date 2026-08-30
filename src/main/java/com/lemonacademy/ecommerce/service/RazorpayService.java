package com.lemonacademy.ecommerce.service;

import com.lemonacademy.ecommerce.dto.RazorpayOrderResponse;
import com.lemonacademy.ecommerce.dto.RazorpayVerifyRequest;
import com.lemonacademy.ecommerce.entity.Order;
import com.lemonacademy.ecommerce.entity.OrderStatus;
import com.lemonacademy.ecommerce.entity.PaymentMethod;
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

    @Value("${razorpay.key.id}")
    private String keyId;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    @Value("${razorpay.webhook.secret:}")
    private String webhookSecret;

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
            orderRequest.put("receipt", order.getOrderNumber() != null ? order.getOrderNumber() : ("receipt_" + order.getId().toString()));

            com.razorpay.Order razorpayOrder = razorpayClient.orders.create(orderRequest);

            String razorpayOrderId = razorpayOrder.get("id");
            order.setRazorpayOrderId(razorpayOrderId);
            order.setPaymentMethod(PaymentMethod.RAZORPAY);
            orderRepository.save(order);

            return RazorpayOrderResponse.builder()
                    .razorpayOrderId(razorpayOrderId)
                    .amount(order.getTotalAmount())
                    .currency("INR")
                    .keyId(keyId)
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
            log.info("Browser verify called for order {} which is already marked as PAID.", order.getId());
            return;
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
                confirmPaymentSuccess(order, request.getRazorpayPaymentId());
                log.info("Successfully verified Razorpay payment for order {}", order.getId());
            } else {
                confirmPaymentFailed(order);
                throw new InvalidOperationException("Payment signature verification failed.");
            }
        } catch (RazorpayException e) {
            log.error("Razorpay signature verification error: {}", e.getMessage());
            throw new InvalidOperationException("Error verifying payment signature: " + e.getMessage());
        }
    }

    @Transactional
    public void processWebhook(String rawPayload, String signature) {
        if (signature == null || signature.isBlank()) {
            throw new InvalidOperationException("Missing Razorpay signature header.");
        }

        String secretToUse = (webhookSecret != null && !webhookSecret.isBlank()) ? webhookSecret : keySecret;
        if (secretToUse == null || secretToUse.isBlank()) {
            throw new InvalidOperationException("Razorpay webhook secret is not configured.");
        }

        try {
            boolean isValid = Utils.verifyWebhookSignature(rawPayload, signature, secretToUse);
            if (!isValid) {
                log.warn("Invalid Razorpay webhook signature.");
                throw new InvalidOperationException("Invalid webhook signature.");
            }
        } catch (RazorpayException e) {
            log.error("Razorpay webhook signature verification failed: {}", e.getMessage());
            throw new InvalidOperationException("Webhook signature verification failed: " + e.getMessage());
        }

        JSONObject eventJson = new JSONObject(rawPayload);
        String event = eventJson.optString("event");
        log.info("Razorpay webhook received with event: {}", event);

        JSONObject payload = eventJson.optJSONObject("payload");
        if (payload == null) {
            log.warn("Razorpay webhook payload is empty for event: {}", event);
            return;
        }

        JSONObject paymentEntity = null;
        if (payload.has("payment") && payload.getJSONObject("payment").has("entity")) {
            paymentEntity = payload.getJSONObject("payment").getJSONObject("entity");
        }

        JSONObject orderEntity = null;
        if (payload.has("order") && payload.getJSONObject("order").has("entity")) {
            orderEntity = payload.getJSONObject("order").getJSONObject("entity");
        }

        String razorpayOrderId = null;
        String razorpayPaymentId = null;
        String receipt = null;

        if (paymentEntity != null) {
            razorpayPaymentId = paymentEntity.optString("id", null);
            razorpayOrderId = paymentEntity.optString("order_id", null);
        }

        if (orderEntity != null) {
            if (razorpayOrderId == null || razorpayOrderId.isBlank()) {
                razorpayOrderId = orderEntity.optString("id", null);
            }
            receipt = orderEntity.optString("receipt", null);
        }

        Order order = null;
        if (razorpayOrderId != null && !razorpayOrderId.isBlank()) {
            order = orderRepository.findByRazorpayOrderId(razorpayOrderId).orElse(null);
        }

        if (order == null && receipt != null && !receipt.isBlank()) {
            order = orderRepository.findByOrderNumber(receipt).orElse(null);
            if (order == null && isStandardUuid(receipt)) {
                order = orderRepository.findById(UUID.fromString(receipt)).orElse(null);
            }
        }

        if (order == null) {
            log.warn("No matching order found for Razorpay webhook. Razorpay Order ID: {}, Payment ID: {}, Receipt: {}",
                    razorpayOrderId, razorpayPaymentId, receipt);
            return;
        }

        log.info("Processing webhook event '{}' for Order ID: {} (#{})", event, order.getId(), order.getOrderNumber());

        switch (event) {
            case "payment.captured":
            case "order.paid":
                confirmPaymentSuccess(order, razorpayPaymentId);
                break;
            case "payment.failed":
                confirmPaymentFailed(order);
                break;
            default:
                log.info("Unhandled Razorpay webhook event '{}'. Ignoring.", event);
                break;
        }
    }

    @Transactional
    public void confirmPaymentSuccess(Order order, String paymentId) {
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            log.info("Order {} is already marked as PAID. Skipping duplicate processing.", order.getId());
            return;
        }

        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentMethod(PaymentMethod.RAZORPAY);
        if (paymentId != null && !paymentId.isBlank()) {
            order.setTransactionId(paymentId);
        }
        if (order.getPaymentVerifiedAt() == null) {
            order.setPaymentVerifiedAt(LocalDateTime.now());
        }
        orderRepository.save(order);
        log.info("Payment confirmed successfully for order {}", order.getId());
    }

    @Transactional
    public void confirmPaymentFailed(Order order) {
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            log.warn("Received payment failure for order {} which is already marked as PAID. Ignoring failure update.", order.getId());
            return;
        }
        order.setPaymentStatus(PaymentStatus.FAILED);
        order.setStatus(OrderStatus.PAYMENT_FAILED);
        order.setPaymentMethod(PaymentMethod.RAZORPAY);
        orderRepository.save(order);
        log.info("Marked payment as FAILED for order {}", order.getId());
    }

    private Order findOrder(String orderId) {
        Order order = orderRepository.findByOrderNumber(orderId).orElse(null);
        if (order == null && isStandardUuid(orderId)) {
            order = orderRepository.findById(UUID.fromString(orderId)).orElse(null);
        }
        if (order == null) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }
        return order;
    }

    private boolean isStandardUuid(String str) {
        if (str == null || str.length() != 36) {
            return false;
        }
        try {
            UUID.fromString(str);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
