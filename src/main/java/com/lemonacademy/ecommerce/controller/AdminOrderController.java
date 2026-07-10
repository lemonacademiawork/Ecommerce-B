package com.lemonacademy.ecommerce.controller;

import java.util.UUID;

import com.lemonacademy.ecommerce.dto.ApiResponse;
import com.lemonacademy.ecommerce.dto.OrderResponse;
import com.lemonacademy.ecommerce.dto.OrderStatusRequest;
import com.lemonacademy.ecommerce.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getAllOrders() {
        List<OrderResponse> response = orderService.getAllOrders();
        return ResponseEntity.ok(ApiResponse.success("All orders retrieved successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderDetails(@PathVariable UUID id) {
        OrderResponse response = orderService.getOrderDetails(id);
        return ResponseEntity.ok(ApiResponse.success("Order details retrieved successfully", response));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable UUID id,
            @Valid @RequestBody OrderStatusRequest request) {
        OrderResponse response = orderService.updateOrderStatus(id, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success("Order status updated successfully", response));
    }
}
