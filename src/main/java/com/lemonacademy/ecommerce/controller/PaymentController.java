package com.lemonacademy.ecommerce.controller;

import com.lemonacademy.ecommerce.dto.ApiResponse;
import com.lemonacademy.ecommerce.dto.QrDetailsResponse;
import com.lemonacademy.ecommerce.dto.RazorpayOrderResponse;
import com.lemonacademy.ecommerce.dto.RazorpayVerifyRequest;
import com.lemonacademy.ecommerce.entity.Order;
import com.lemonacademy.ecommerce.repository.OrderRepository;
import com.lemonacademy.ecommerce.service.QrPaymentService;
import com.lemonacademy.ecommerce.service.RazorpayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.exception.ResourceNotFoundException;
import com.lemonacademy.ecommerce.exception.UnauthorizedAccessException;
import com.lemonacademy.ecommerce.repository.UserRepository;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final QrPaymentService qrPaymentService;
    private final RazorpayService razorpayService;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    @GetMapping("/qr")
    public ResponseEntity<ApiResponse<QrDetailsResponse>> getQrDetails() {
        String absoluteImageUrl = org.springframework.web.servlet.support.ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/images/qr.jpeg")
                .toUriString();

        QrDetailsResponse response = QrDetailsResponse.builder()
                .merchantName("Lemon House")
                .upiId("")
                .qrImageUrl(absoluteImageUrl)
                .build();
        return ResponseEntity.ok(ApiResponse.success("QR Details retrieved successfully", response));
    }

    @PostMapping("/qr/submit")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<ApiResponse<String>> submitQrPayment(
            @RequestParam("orderId") String orderId,
            @RequestParam(value = "transactionId", required = false) String transactionId,
            @RequestPart(value = "paymentScreenshot") MultipartFile paymentScreenshot) {

        if (paymentScreenshot == null || paymentScreenshot.isEmpty()) {
            throw new com.lemonacademy.ecommerce.exception.InvalidOperationException(
                    "Payment screenshot is required. Please upload a screenshot of your payment where the transaction ID is clearly visible.");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Order order = orderRepository.findByOrderNumber(orderId).orElse(null);
        if (order == null) {
            order = orderRepository.findById(java.util.UUID.fromString(orderId))
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        }

        if (!order.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedAccessException("You are not authorized to submit payment for this order");
        }

        qrPaymentService.processPayment(order, transactionId, paymentScreenshot);

        return ResponseEntity.ok(ApiResponse.success(
                "QR Payment submitted successfully and is pending verification. " +
                "Note: Please ensure the transaction ID is visible in the screenshot, otherwise your payment will not be confirmed.", null));
    }

    // --- Razorpay Endpoints ---

    @PostMapping("/razorpay/create-order")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<ApiResponse<RazorpayOrderResponse>> createRazorpayOrder(@RequestParam("orderId") String orderId) {
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Order order = orderRepository.findByOrderNumber(orderId).orElse(null);
        if (order == null) {
            order = orderRepository.findById(java.util.UUID.fromString(orderId))
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        }

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

        Order order = orderRepository.findByOrderNumber(request.getInternalOrderId()).orElse(null);
        if (order == null) {
            order = orderRepository.findById(java.util.UUID.fromString(request.getInternalOrderId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + request.getInternalOrderId()));
        }

        if (!order.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedAccessException("You are not authorized to verify payment for this order");
        }

        razorpayService.verifyPayment(request);
        return ResponseEntity.ok(ApiResponse.success("Payment verified successfully", null));
    }
}
