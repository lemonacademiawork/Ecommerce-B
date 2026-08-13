package com.lemonacademy.ecommerce.controller;

import java.util.UUID;

import com.lemonacademy.ecommerce.dto.ApiResponse;
import com.lemonacademy.ecommerce.dto.PageResponseDto;
import com.lemonacademy.ecommerce.dto.ProductReviewResponseDto;
import com.lemonacademy.ecommerce.service.ProductReviewService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Reviews Management", description = "Admin Review Management APIs")
public class AdminReviewController {

    private final ProductReviewService reviewService;

    @GetMapping
    @Operation(summary = "Search/filter all product reviews")
    public ResponseEntity<ApiResponse<PageResponseDto<ProductReviewResponseDto>>> searchReviews(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) UUID productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("asc") 
                ? Sort.by(sortBy).ascending() 
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        PageResponseDto<ProductReviewResponseDto> response = reviewService.searchAndFilterReviews(query, rating, productId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Reviews retrieved successfully", response));
    }

    @DeleteMapping("/{reviewId}")
    @Operation(summary = "Admin delete review by ID")
    public ResponseEntity<ApiResponse<Void>> deleteReview(@PathVariable UUID reviewId) {
        reviewService.deleteReview(reviewId, null, true);
        return ResponseEntity.ok(ApiResponse.success("Review deleted successfully by admin", null));
    }
}
