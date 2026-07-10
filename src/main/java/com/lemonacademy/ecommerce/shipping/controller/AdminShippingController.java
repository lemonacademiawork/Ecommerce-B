package com.lemonacademy.ecommerce.shipping.controller;

import java.util.UUID;

import com.lemonacademy.ecommerce.dto.ApiResponse;
import com.lemonacademy.ecommerce.entity.Order;
import com.lemonacademy.ecommerce.repository.OrderRepository;
import com.lemonacademy.ecommerce.shipping.dto.*;
import com.lemonacademy.ecommerce.shipping.service.*;
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

    @PostMapping("/book")
    @Operation(summary = "Book shipment manually", description = "Allows admins to manually book a shipment with iCarry for a specific order.")
    public ResponseEntity<ApiResponse<Order>> bookShipment(@Valid @RequestBody BookShipmentRequest request) {
        log.info("Admin manual shipment booking triggered for order ID: {}", request.getOrderId());
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found with ID: " + request.getOrderId()));
        Order updatedOrder = shipmentService.bookShipmentForOrder(order);
        return ResponseEntity.ok(ApiResponse.success("Shipment booked successfully", updatedOrder));
    }

    @PostMapping("/cancel")
    @Operation(summary = "Cancel shipping booking", description = "Cancels an active shipment booked with iCarry and marks status as CANCELLED.")
    public ResponseEntity<ApiResponse<Order>> cancelShipment(@Valid @RequestBody CancelShipmentRequest request) {
        log.info("Admin shipment cancellation triggered for order ID: {}", request.getOrderId());
        Order updatedOrder = shipmentService.cancelShipment(request.getOrderId());
        return ResponseEntity.ok(ApiResponse.success("Shipment cancelled successfully", updatedOrder));
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
    public ResponseEntity<ApiResponse<String>> generateLabel(@Parameter(description = "The database Order ID") @PathVariable UUID orderId) {
        log.info("Admin label generation requested for order ID: {}", orderId);
        String labelUrl = labelService.generateLabel(orderId);
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
    public ResponseEntity<ApiResponse<Order>> requestPickup(@Parameter(description = "The database Order ID") @PathVariable UUID orderId) {
        log.info("Admin scheduling pickup request for order ID: {}", orderId);
        Order updatedOrder = pickupService.requestPickup(orderId);
        return ResponseEntity.ok(ApiResponse.success("Pickup scheduled successfully", updatedOrder));
    }
}
