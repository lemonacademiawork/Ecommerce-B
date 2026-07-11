package com.lemonacademy.ecommerce.controller;

import com.lemonacademy.ecommerce.dto.ApiResponse;
import com.lemonacademy.ecommerce.dto.QrDetailsResponse;
import com.lemonacademy.ecommerce.entity.Order;
import com.lemonacademy.ecommerce.repository.OrderRepository;
import com.lemonacademy.ecommerce.service.QrPaymentService;
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
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    @GetMapping("/qr")
    public ResponseEntity<ApiResponse<QrDetailsResponse>> getQrDetails() {
        QrDetailsResponse response = QrDetailsResponse.builder()
                .merchantName("MANISHI NIGAM")
                .upiId("")
                .qrImageUrl("/images/qr.jpeg")
                .build();
        return ResponseEntity.ok(ApiResponse.success("QR Details retrieved successfully", response));
    }

    @PostMapping("/qr/submit")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<String>> submitQrPayment(
            @RequestParam("orderId") String orderId,
            @RequestParam(value = "transactionId", required = false) String transactionId,
            @RequestPart(value = "paymentScreenshot", required = false) MultipartFile paymentScreenshot) {

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

        return ResponseEntity.ok(ApiResponse.success("QR Payment submitted successfully and is pending verification", null));
    }
}
