package com.lemonacademy.ecommerce.shipping.controller;

import java.util.UUID;

import com.lemonacademy.ecommerce.dto.ApiResponse;
import com.lemonacademy.ecommerce.dto.OrderResponse;
import com.lemonacademy.ecommerce.entity.Order;
import com.lemonacademy.ecommerce.repository.OrderRepository;
import com.lemonacademy.ecommerce.shipping.dto.*;
import com.lemonacademy.ecommerce.shipping.service.*;
import com.lemonacademy.ecommerce.service.OrderService;
import com.lemonacademy.ecommerce.service.UpstashRedisService;
import com.lemonacademy.ecommerce.shipping.config.IcarryConfig;
import com.lemonacademy.ecommerce.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/shipping")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Shipping API", description = "Endpoints for managing iCarry shipping operations, estimates, cancellations, labels, and pickups.")
@Slf4j
public class AdminShippingController {

    private final IcarryShipmentService shipmentService;
    private final IcarryEstimateService estimateService;
    private final IcarryTrackingService trackingService;
    private final IcarryLabelService labelService;
    private final IcarryPickupService pickupService;
    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private UpstashRedisService redisService;

    @org.springframework.beans.factory.annotation.Autowired
    private IcarryConfig config;

    private Order resolveOrder(String id) {
        Order order = null;
        try {
            UUID uuid = UUID.fromString(id);
            order = orderRepository.findById(uuid).orElse(null);
        } catch (IllegalArgumentException e) {
            // Not a UUID, try orderNumber
        }
        if (order == null) {
            order = orderRepository.findByOrderNumber(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found with identifier: " + id));
        }
        return order;
    }

    @PostMapping("/book")
    @Operation(summary = "Book shipment manually", description = "Allows admins to manually book a shipment with iCarry for a specific order.")
    public ResponseEntity<ApiResponse<OrderResponse>> bookShipment(@Valid @RequestBody BookShipmentRequest request) {
        log.info("Admin manual shipment booking triggered for order ID: {}", request.getOrderId());
        Order order = resolveOrder(request.getOrderId());
        Order updatedOrder = shipmentService.bookShipmentForOrder(order, request.getPickupAddressId());
        OrderResponse response = orderService.getOrderDetails(updatedOrder.getId().toString());
        return ResponseEntity.ok(ApiResponse.success("Shipment booked successfully", response));
    }

    @PostMapping("/cancel")
    @Operation(summary = "Cancel shipping booking", description = "Cancels an active shipment booked with iCarry and marks status as CANCELLED.")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelShipment(@Valid @RequestBody CancelShipmentRequest request) {
        log.info("Admin shipment cancellation triggered for order ID: {}", request.getOrderId());
        Order order = resolveOrder(request.getOrderId());
        Order updatedOrder = shipmentService.cancelShipment(order.getId());
        OrderResponse response = orderService.getOrderDetails(updatedOrder.getId().toString());
        return ResponseEntity.ok(ApiResponse.success("Shipment cancelled successfully", response));
    }

    @PostMapping("/estimate")
    @Operation(summary = "Calculate shipping estimate", description = "Retrieves estimated courier rates and ETAs based on weight, dimensions, and pincodes.")
    public ResponseEntity<ApiResponse<List<CourierEstimateResponse>>> getEstimate(@Valid @RequestBody ShippingEstimateRequest request) {
        log.info("Admin manual estimate requested");
        List<CourierEstimateResponse> estimates = estimateService.getEstimate(request);
        return ResponseEntity.ok(ApiResponse.success("Estimates retrieved successfully", estimates));
    }

    @GetMapping("/track/{tracking}")
    @Operation(summary = "Track shipment by AWB", description = "Fetches real-time status and historical transit events from iCarry using AWB or tracking number.")
    public ResponseEntity<ApiResponse<TrackingResponse>> trackShipment(@Parameter(description = "The AWB tracking number") @PathVariable String tracking) {
        log.info("Admin tracking request for AWB: {}", tracking);
        TrackingResponse response = trackingService.trackShipment(tracking);
        return ResponseEntity.ok(ApiResponse.success("Tracking details retrieved", response));
    }

    @GetMapping("/label/{orderId}")
    @Operation(summary = "Generate/Download shipping label", description = "Requests a printable PDF label URL from iCarry and saves it in the order's database entry.")
    public ResponseEntity<ApiResponse<String>> generateLabel(@Parameter(description = "The database Order ID or order number") @PathVariable String orderId) {
        log.info("Admin label generation requested for order ID: {}", orderId);
        Order order = resolveOrder(orderId);
        String labelUrl = labelService.generateLabel(order.getId());
        return ResponseEntity.ok(ApiResponse.success("Label generated successfully", labelUrl));
    }

    @PostMapping("/pickup/address")
    @Operation(summary = "Create/Update pickup address", description = "Registers or updates warehouse pickup address credentials in the iCarry system.")
    public ResponseEntity<ApiResponse<String>> createOrUpdatePickupAddress(@Valid @RequestBody PickupAddressRequest request) {
        log.info("Admin creating/updating pickup address");
        String addressId = pickupService.createOrUpdatePickupAddress(request);
        return ResponseEntity.ok(ApiResponse.success("Pickup address saved successfully", addressId));
    }

    @PostMapping("/pickup/request/{orderId}")
    @Operation(summary = "Schedule package courier pickup", description = "Dispatches a pickup driver allocation request for the booked shipment's AWB package.")
    public ResponseEntity<ApiResponse<OrderResponse>> requestPickup(@Parameter(description = "The database Order ID or order number") @PathVariable String orderId) {
        log.info("Admin scheduling pickup request for order ID: {}", orderId);
        Order order = resolveOrder(orderId);
        Order updatedOrder = pickupService.requestPickup(order.getId());
        OrderResponse response = orderService.getOrderDetails(updatedOrder.getId().toString());
        return ResponseEntity.ok(ApiResponse.success("Pickup scheduled successfully", response));
    }

    @PostMapping("/pickup/address/id/{id}")
    @Operation(summary = "Directly set the cached pickup address ID", description = "Saves the specified iCarry pickup address ID in the system cache.")
    public ResponseEntity<ApiResponse<String>> setPickupAddressId(@PathVariable String id) {
        log.info("Directly setting cached pickup address ID to: {}", id);
        if (redisService != null) {
            try {
                redisService.set("icarry_pickup_address_id", id, 31536000);
            } catch (Exception e) {
                log.warn("Failed to set pickup address ID in Redis: {}", e.getMessage());
            }
        }
        return ResponseEntity.ok(ApiResponse.success("Pickup address ID updated in cache", id));
    }

    @GetMapping("/pickup/address/id")
    @Operation(summary = "Get the active pickup address ID", description = "Retrieves the active pickup address ID from cache or configuration.")
    public ResponseEntity<ApiResponse<String>> getPickupAddressId() {
        String id = null;
        if (redisService != null) {
            try {
                id = redisService.get("icarry_pickup_address_id");
            } catch (Exception e) {
                log.warn("Failed to get pickup address ID from Redis: {}", e.getMessage());
            }
        }
        if (id == null || id.isEmpty()) {
            if (config != null) {
                id = config.getPickupAddressId();
            } else {
                id = "PRIMARY";
            }
        }
        return ResponseEntity.ok(ApiResponse.success("Active pickup address ID retrieved", id));
    }
}
