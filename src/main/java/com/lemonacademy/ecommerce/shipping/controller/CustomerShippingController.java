package com.lemonacademy.ecommerce.shipping.controller;

import com.lemonacademy.ecommerce.dto.ApiResponse;
import com.lemonacademy.ecommerce.entity.Order;
import com.lemonacademy.ecommerce.entity.Role;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.exception.UnauthorizedAccessException;
import com.lemonacademy.ecommerce.repository.OrderRepository;
import com.lemonacademy.ecommerce.shipping.dto.TrackingResponse;
import com.lemonacademy.ecommerce.shipping.service.IcarryTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
@Tag(name = "Customer Shipping API", description = "Endpoints for customers to track their own orders.")
@Slf4j
public class CustomerShippingController {

    private final IcarryTrackingService trackingService;
    private final OrderRepository orderRepository;

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (User) authentication.getPrincipal();
    }

    @GetMapping("/track/{tracking}")
    @Operation(summary = "Track own shipment", description = "Allows an authenticated customer to query transit details for their own shipment AWB, verifying order ownership.")
    public ResponseEntity<ApiResponse<TrackingResponse>> trackOwnShipment(
            @Parameter(description = "The AWB tracking reference") @PathVariable String tracking) {
        log.info("Customer tracking request for AWB: {}", tracking);
        User user = getAuthenticatedUser();
        
        Order order = orderRepository.findByAwbNumber(tracking)
                .orElseGet(() -> orderRepository.findByShipmentId(tracking)
                .orElseThrow(() -> new RuntimeException("No order found matching tracking reference: " + tracking)));

        // Security check: Customer can only track their own order. Admins can track anything.
        boolean isAdmin = user.getRole() == Role.ADMIN;
        if (!isAdmin && !order.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedAccessException("You are not authorized to track this shipment.");
        }

        TrackingResponse response = trackingService.trackShipment(tracking);
        return ResponseEntity.ok(ApiResponse.success("Tracking details retrieved", response));
    }
}
