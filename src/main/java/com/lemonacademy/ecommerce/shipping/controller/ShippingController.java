package com.lemonacademy.ecommerce.shipping.controller;

import com.lemonacademy.ecommerce.dto.ApiResponse;
import com.lemonacademy.ecommerce.shipping.config.IcarryConfig;
import com.lemonacademy.ecommerce.shipping.dto.CourierEstimateResponse;
import com.lemonacademy.ecommerce.shipping.dto.CustomerEstimateRequest;
import com.lemonacademy.ecommerce.shipping.dto.ShippingEstimateRequest;
import com.lemonacademy.ecommerce.shipping.service.IcarryEstimateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.entity.Cart;
import com.lemonacademy.ecommerce.entity.CartItem;
import com.lemonacademy.ecommerce.entity.Product;
import com.lemonacademy.ecommerce.repository.CartRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
@Tag(name = "Shipping", description = "Customer-facing shipping estimate APIs")
@Slf4j
public class ShippingController {

    private final IcarryEstimateService estimateService;
    private final IcarryConfig icarryConfig;
    private final CartRepository cartRepository;

    @PostMapping("/estimate")
    @Operation(summary = "Get shipping estimate", description = "Returns estimated delivery charges and ETAs from available couriers based on destination pincode.")
    public ResponseEntity<ApiResponse<List<CourierEstimateResponse>>> getShippingEstimate(
            @Valid @RequestBody CustomerEstimateRequest request) {
        log.info("Customer shipping estimate requested for pincode: {}", request.getDestinationPincode());

        int weight = request.getWeight() != null ? request.getWeight() : icarryConfig.getDefaultWeight();
        int length = 10;
        int breadth = 10;
        int height = 10;

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof User) {
                User user = (User) auth.getPrincipal();
                Cart cart = cartRepository.findByUser(user).orElse(null);
                if (cart != null && !cart.getItems().isEmpty()) {
                    int totalWeight = 0;
                    int maxLen = 0, maxBre = 0, maxHei = 0;
                    for (CartItem item : cart.getItems()) {
                        Product p = item.getProduct();
                        totalWeight += (p.getWeight() != null ? p.getWeight() : icarryConfig.getDefaultWeight()) * item.getQuantity();
                        maxLen = Math.max(maxLen, p.getLength() != null ? p.getLength() : 10);
                        maxBre = Math.max(maxBre, p.getBreadth() != null ? p.getBreadth() : 10);
                        maxHei = Math.max(maxHei, p.getHeight() != null ? p.getHeight() : 10);
                    }
                    weight = totalWeight;
                    length = maxLen;
                    breadth = maxBre;
                    height = maxHei;
                    log.info("Calculated total shipping weight from cart: {}g", weight);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to calculate weight from cart, using default/request weight: {}", e.getMessage());
        }

        ShippingEstimateRequest estimateRequest = ShippingEstimateRequest.builder()
                .originPincode(icarryConfig.getOriginPincode())
                .destinationPincode(request.getDestinationPincode())
                .weight(weight)
                .length(length)
                .breadth(breadth)
                .height(height)
                .parcelType("P") // Prepaid
                .parcelValue(request.getOrderValue())
                .build();

        List<CourierEstimateResponse> estimates = estimateService.getEstimate(estimateRequest);
        return ResponseEntity.ok(ApiResponse.success("Shipping estimates retrieved successfully", estimates));
    }
}
