package com.lemonacademy.ecommerce.controller;

import java.util.UUID;
import java.util.List;

import com.lemonacademy.ecommerce.dto.ApiResponse;
import com.lemonacademy.ecommerce.dto.ProductVariantRequestDto;
import com.lemonacademy.ecommerce.dto.ProductVariantResponseDto;
import com.lemonacademy.ecommerce.service.ProductVariantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class ProductVariantController {

    private final ProductVariantService productVariantService;

    // --- Admin Endpoints ---

    @PostMapping("/api/admin/products/{productId}/variants")
    public ResponseEntity<ApiResponse<ProductVariantResponseDto>> addVariant(
            @PathVariable UUID productId,
            @Valid @RequestBody ProductVariantRequestDto dto) {
        ProductVariantResponseDto variant = productVariantService.addVariant(productId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Variant added successfully", variant));
    }

    @PutMapping("/api/admin/variants/{id}")
    public ResponseEntity<ApiResponse<ProductVariantResponseDto>> updateVariant(
            @PathVariable UUID id,
            @Valid @RequestBody ProductVariantRequestDto dto) {
        ProductVariantResponseDto variant = productVariantService.updateVariant(id, dto);
        return ResponseEntity.ok(ApiResponse.success("Variant updated successfully", variant));
    }

    @DeleteMapping("/api/admin/variants/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteVariant(@PathVariable UUID id) {
        productVariantService.deleteVariant(id);
        return ResponseEntity.ok(ApiResponse.success("Variant deleted successfully", null));
    }

    // --- Public Endpoints ---

    @GetMapping("/api/products/{productId}/variants")
    public ResponseEntity<ApiResponse<List<ProductVariantResponseDto>>> getVariants(@PathVariable UUID productId) {
        List<ProductVariantResponseDto> variants = productVariantService.getActiveVariantsByProductId(productId);
        return ResponseEntity.ok(ApiResponse.success("Variants retrieved successfully", variants));
    }
    
    @GetMapping("/api/admin/products/{productId}/variants")
    public ResponseEntity<ApiResponse<List<ProductVariantResponseDto>>> getAllVariantsForAdmin(@PathVariable UUID productId) {
        List<ProductVariantResponseDto> variants = productVariantService.getVariantsByProductId(productId);
        return ResponseEntity.ok(ApiResponse.success("All variants retrieved successfully", variants));
    }
}
