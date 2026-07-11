package com.lemonacademy.ecommerce.controller;

import com.lemonacademy.ecommerce.dto.AdminPaymentResponse;
import com.lemonacademy.ecommerce.dto.ApiResponse;
import com.lemonacademy.ecommerce.entity.Order;
import com.lemonacademy.ecommerce.entity.OrderStatus;
import com.lemonacademy.ecommerce.entity.PaymentStatus;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.exception.ResourceNotFoundException;
import com.lemonacademy.ecommerce.repository.OrderRepository;
import com.lemonacademy.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<AdminPaymentResponse>>> getPendingPayments() {
        List<Order> pendingOrders = orderRepository.findAll().stream()
                .filter(o -> o.getPaymentStatus() == PaymentStatus.PENDING_VERIFICATION)
                .collect(Collectors.toList());

        List<AdminPaymentResponse> response = pendingOrders.stream().map(this::mapToAdminPaymentResponse).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Pending payments retrieved successfully", response));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<AdminPaymentResponse>> getPaymentDetails(@PathVariable String orderId) {
        Order order = findOrder(orderId);
        return ResponseEntity.ok(ApiResponse.success("Payment details retrieved successfully", mapToAdminPaymentResponse(order)));
    }

    @PutMapping("/{orderId}/approve")
    @Transactional
    public ResponseEntity<ApiResponse<String>> approvePayment(@PathVariable String orderId) {
        Order order = findOrder(orderId);

        if (order.getPaymentStatus() != PaymentStatus.PENDING_VERIFICATION) {
            throw new IllegalStateException("Payment status is not PENDING_VERIFICATION");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User admin = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentVerifiedAt(LocalDateTime.now());
        order.setVerifiedByAdminId(admin.getId());

        orderRepository.save(order);

        return ResponseEntity.ok(ApiResponse.success("Payment approved successfully", null));
    }

    @PutMapping("/{orderId}/reject")
    @Transactional
    public ResponseEntity<ApiResponse<String>> rejectPayment(@PathVariable String orderId) {
        Order order = findOrder(orderId);

        if (order.getPaymentStatus() != PaymentStatus.PENDING_VERIFICATION) {
            throw new IllegalStateException("Payment status is not PENDING_VERIFICATION");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User admin = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        order.setPaymentStatus(PaymentStatus.FAILED);
        order.setStatus(OrderStatus.PAYMENT_FAILED);
        order.setPaymentVerifiedAt(LocalDateTime.now());
        order.setVerifiedByAdminId(admin.getId());

        orderRepository.save(order);

        return ResponseEntity.ok(ApiResponse.success("Payment rejected successfully", null));
    }

    private Order findOrder(String orderId) {
        Order order = orderRepository.findByOrderNumber(orderId).orElse(null);
        if (order == null) {
            order = orderRepository.findById(java.util.UUID.fromString(orderId))
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        }
        return order;
    }

    private AdminPaymentResponse mapToAdminPaymentResponse(Order order) {
        return AdminPaymentResponse.builder()
                .orderId(order.getId().toString())
                .orderNumber(order.getOrderNumber())
                .customerEmail(order.getUser().getEmail())
                .totalAmount(order.getTotalAmount())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .transactionId(order.getTransactionId())
                .paymentScreenshotUrl(order.getPaymentScreenshotUrl())
                .orderDate(order.getCreatedAt())
                .paymentVerifiedAt(order.getPaymentVerifiedAt())
                .build();
    }
}
