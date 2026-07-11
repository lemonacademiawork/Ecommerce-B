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

    @PostMapping("/estimate")
    @Operation(summary = "Get shipping estimate", description = "Returns estimated delivery charges and ETAs from available couriers based on destination pincode.")
    public ResponseEntity<ApiResponse<List<CourierEstimateResponse>>> getShippingEstimate(
            @Valid @RequestBody CustomerEstimateRequest request) {
        log.info("Customer shipping estimate requested for pincode: {}", request.getDestinationPincode());

        int weight = request.getWeight() != null ? request.getWeight() : icarryConfig.getDefaultWeight();

        ShippingEstimateRequest estimateRequest = ShippingEstimateRequest.builder()
                .originPincode(icarryConfig.getOriginPincode())
                .destinationPincode(request.getDestinationPincode())
                .weight(weight)
                .parcelType("P") // Prepaid
                .parcelValue(request.getOrderValue())
                .build();

        List<CourierEstimateResponse> estimates = estimateService.getEstimate(estimateRequest);
        return ResponseEntity.ok(ApiResponse.success("Shipping estimates retrieved successfully", estimates));
    }
}
