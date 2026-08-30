package com.lemonacademy.ecommerce.controller;

import com.lemonacademy.ecommerce.dto.ApiResponse;
import com.lemonacademy.ecommerce.dto.RazorpayOrderResponse;
import com.lemonacademy.ecommerce.dto.RazorpayVerifyRequest;
import com.lemonacademy.ecommerce.entity.Order;
import com.lemonacademy.ecommerce.repository.OrderRepository;
import com.lemonacademy.ecommerce.service.RazorpayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.exception.ResourceNotFoundException;
import com.lemonacademy.ecommerce.exception.UnauthorizedAccessException;
import com.lemonacademy.ecommerce.repository.UserRepository;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final RazorpayService razorpayService;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    // --- Razorpay Endpoints ---

    @PostMapping("/razorpay/create-order")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<ApiResponse<RazorpayOrderResponse>> createRazorpayOrder(@RequestParam("orderId") String orderId) {
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Order order = findOrder(orderId);

        if (!order.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedAccessException("You are not authorized to create payment for this order");
        }

        RazorpayOrderResponse response = razorpayService.createRazorpayOrder(orderId);
        return ResponseEntity.ok(ApiResponse.success("Razorpay Order created successfully", response));
    }

    @PostMapping("/razorpay/verify")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<ApiResponse<String>> verifyRazorpayPayment(@jakarta.validation.Valid @RequestBody RazorpayVerifyRequest request) {
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Order order = findOrder(request.getInternalOrderId());

        if (!order.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedAccessException("You are not authorized to verify payment for this order");
        }

        razorpayService.verifyPayment(request);
        return ResponseEntity.ok(ApiResponse.success("Payment verified successfully", null));
    }

    private Order findOrder(String orderId) {
        Order order = orderRepository.findByOrderNumber(orderId).orElse(null);
        if (order == null && isStandardUuid(orderId)) {
            order = orderRepository.findById(java.util.UUID.fromString(orderId)).orElse(null);
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
            java.util.UUID.fromString(str);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
