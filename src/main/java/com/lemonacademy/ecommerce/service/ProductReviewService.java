package com.lemonacademy.ecommerce.service;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import com.lemonacademy.ecommerce.dto.PageResponseDto;
import com.lemonacademy.ecommerce.dto.ProductRatingSummaryDto;
import com.lemonacademy.ecommerce.dto.ProductReviewRequestDto;
import com.lemonacademy.ecommerce.dto.ProductReviewResponseDto;

public interface ProductReviewService {

    boolean canUserReview(UUID productId, UUID userId);

    ProductReviewResponseDto createReview(UUID productId, UUID userId, ProductReviewRequestDto requestDto, List<MultipartFile> images);

    ProductReviewResponseDto updateReview(UUID reviewId, UUID userId, ProductReviewRequestDto requestDto, List<MultipartFile> newImages, List<String> retainedImages);

    void deleteReview(UUID reviewId, UUID userId, boolean isAdmin);

    PageResponseDto<ProductReviewResponseDto> getProductReviews(UUID productId, Pageable pageable);

    ProductReviewResponseDto getMyReview(UUID productId, UUID userId);

    ProductRatingSummaryDto getProductRatingSummary(UUID productId);

    PageResponseDto<ProductReviewResponseDto> searchAndFilterReviews(String query, Integer rating, UUID productId, Pageable pageable);
}
