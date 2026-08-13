package com.lemonacademy.ecommerce.controller;

import java.util.List;
import java.util.UUID;

import com.lemonacademy.ecommerce.dto.ApiResponse;
import com.lemonacademy.ecommerce.dto.PageResponseDto;
import com.lemonacademy.ecommerce.dto.ProductReviewRequestDto;
import com.lemonacademy.ecommerce.dto.ProductReviewResponseDto;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.exception.UnauthorizedAccessException;
import com.lemonacademy.ecommerce.service.ProductReviewService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Product Reviews", description = "Product Review and Rating Management APIs")
public class ProductReviewController {

    private final ProductReviewService reviewService;

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User)) {
            throw new UnauthorizedAccessException("User not authenticated");
        }
        return (User) authentication.getPrincipal();
    }

    @PostMapping(value = "/products/{productId}/reviews", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Submit a product review")
    public ResponseEntity<ApiResponse<ProductReviewResponseDto>> createReview(
            @PathVariable UUID productId,
            @RequestParam("rating") Integer rating,
            @RequestParam(value = "comment", required = false) String comment,
            @RequestParam(value = "images", required = false) List<MultipartFile> images) {

        User user = getAuthenticatedUser();
        ProductReviewRequestDto requestDto = ProductReviewRequestDto.builder()
                .rating(rating)
                .comment(comment)
                .build();

        ProductReviewResponseDto response = reviewService.createReview(productId, user.getId(), requestDto, images);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Review submitted successfully", response));
    }

    @GetMapping("/products/{productId}/reviews")
    @Operation(summary = "Get product reviews paginated")
    public ResponseEntity<ApiResponse<PageResponseDto<ProductReviewResponseDto>>> getProductReviews(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "latest") String sortBy) {

        Sort sort = switch (sortBy.toLowerCase()) {
            case "highest" -> Sort.by("rating").descending();
            case "lowest" -> Sort.by("rating").ascending();
            default -> Sort.by("createdAt").descending();
        };

        Pageable pageable = PageRequest.of(page, size, sort);
        PageResponseDto<ProductReviewResponseDto> response = reviewService.getProductReviews(productId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Reviews retrieved successfully", response));
    }

    @GetMapping("/products/{productId}/reviews/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get current user's review for a product")
    public ResponseEntity<ApiResponse<ProductReviewResponseDto>> getMyReview(@PathVariable UUID productId) {
        User user = getAuthenticatedUser();
        ProductReviewResponseDto response = reviewService.getMyReview(productId, user.getId());
        return ResponseEntity.ok(ApiResponse.success("Review retrieved successfully", response));
    }

    @PutMapping(value = "/reviews/{reviewId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Update review details and photos")
    public ResponseEntity<ApiResponse<ProductReviewResponseDto>> updateReview(
            @PathVariable UUID reviewId,
            @RequestParam("rating") Integer rating,
            @RequestParam(value = "comment", required = false) String comment,
            @RequestParam(value = "retainedImages", required = false) List<String> retainedImages,
            @RequestParam(value = "images", required = false) List<MultipartFile> images) {

        User user = getAuthenticatedUser();
        ProductReviewRequestDto requestDto = ProductReviewRequestDto.builder()
                .rating(rating)
                .comment(comment)
                .build();

        ProductReviewResponseDto response = reviewService.updateReview(reviewId, user.getId(), requestDto, images, retainedImages);
        return ResponseEntity.ok(ApiResponse.success("Review updated successfully", response));
    }

    @DeleteMapping("/reviews/{reviewId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @Operation(summary = "Delete user's own review")
    public ResponseEntity<ApiResponse<Void>> deleteReview(@PathVariable UUID reviewId) {
        User user = getAuthenticatedUser();
        boolean isAdmin = user.getRole().name().equals("ADMIN");
        reviewService.deleteReview(reviewId, user.getId(), isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Review deleted successfully", null));
    }

    @GetMapping("/products/{productId}/reviews/can-review")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Check if current user is eligible to write a review")
    public ResponseEntity<ApiResponse<Boolean>> canReview(@PathVariable UUID productId) {
        User user = getAuthenticatedUser();
        boolean canReview = reviewService.canUserReview(productId, user.getId());
        return ResponseEntity.ok(ApiResponse.success("Review eligibility checked", canReview));
    }
}
