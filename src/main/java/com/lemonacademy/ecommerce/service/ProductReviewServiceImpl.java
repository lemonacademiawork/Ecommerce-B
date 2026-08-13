package com.lemonacademy.ecommerce.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.lemonacademy.ecommerce.dto.PageResponseDto;
import com.lemonacademy.ecommerce.dto.ProductRatingSummaryDto;
import com.lemonacademy.ecommerce.dto.ProductReviewRequestDto;
import com.lemonacademy.ecommerce.dto.ProductReviewResponseDto;
import com.lemonacademy.ecommerce.entity.Product;
import com.lemonacademy.ecommerce.entity.ProductReview;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.exception.InvalidOperationException;
import com.lemonacademy.ecommerce.exception.ResourceNotFoundException;
import com.lemonacademy.ecommerce.exception.UnauthorizedAccessException;
import com.lemonacademy.ecommerce.repository.OrderRepository;
import com.lemonacademy.ecommerce.repository.ProductRepository;
import com.lemonacademy.ecommerce.repository.ProductReviewRepository;
import com.lemonacademy.ecommerce.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProductReviewServiceImpl implements ProductReviewService {

    private final ProductReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final CloudinaryService cloudinaryService;
    private final UpstashRedisService redisService;

    private static final List<String> ALLOWED_IMAGE_TYPES = List.of("image/jpeg", "image/png", "image/jpg", "image/webp");
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024; // 5MB

    @Override
    @Transactional(readOnly = true)
    public boolean canUserReview(UUID productId, UUID userId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found");
        }
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found");
        }

        // Already reviewed?
        if (reviewRepository.existsByProductIdAndUserIdAndDeletedAtIsNull(productId, userId)) {
            return false;
        }

        // Has purchased and delivered?
        return orderRepository.hasUserPurchasedProductAndDelivered(userId, productId);
    }

    @Override
    @Transactional
    public ProductReviewResponseDto createReview(UUID productId, UUID userId, ProductReviewRequestDto requestDto, List<MultipartFile> images) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (reviewRepository.existsByProductIdAndUserIdAndDeletedAtIsNull(productId, userId)) {
            throw new InvalidOperationException("You have already reviewed this product");
        }

        if (!orderRepository.hasUserPurchasedProductAndDelivered(userId, productId)) {
            throw new InvalidOperationException("You can only review products you have successfully purchased and had delivered");
        }

        if (requestDto.getRating() == null || requestDto.getRating() < 1 || requestDto.getRating() > 5) {
            throw new IllegalArgumentException("Rating is mandatory and must be between 1 and 5");
        }

        if (requestDto.getComment() != null && requestDto.getComment().length() > 1000) {
            throw new IllegalArgumentException("Comment must be less than 1000 characters");
        }

        int imagesCount = (images != null) ? images.size() : 0;
        if (imagesCount > 2) {
            throw new IllegalArgumentException("A review can have a maximum of 2 images");
        }

        List<String> uploadedUrls = new ArrayList<>();
        if (images != null && !images.isEmpty()) {
            for (MultipartFile img : images) {
                validateImage(img);
            }
            try {
                uploadedUrls = cloudinaryService.uploadImages(images, "lemon-house/reviews");
            } catch (IOException e) {
                throw new RuntimeException("Cloudinary image upload failed: " + e.getMessage(), e);
            }
        }

        ProductReview review = ProductReview.builder()
                .product(product)
                .user(user)
                .rating(requestDto.getRating())
                .comment(requestDto.getComment())
                .photoUrl1(uploadedUrls.size() > 0 ? uploadedUrls.get(0) : null)
                .photoUrl2(uploadedUrls.size() > 1 ? uploadedUrls.get(1) : null)
                .verifiedPurchase(true)
                .build();

        ProductReview saved = reviewRepository.save(review);
        evictCache();

        return convertToDto(saved);
    }

    @Override
    @Transactional
    public ProductReviewResponseDto updateReview(UUID reviewId, UUID userId, ProductReviewRequestDto requestDto, List<MultipartFile> newImages, List<String> retainedImages) {
        ProductReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));

        if (!review.getUser().getId().equals(userId)) {
            throw new UnauthorizedAccessException("You are not authorized to edit this review");
        }

        if (review.getDeletedAt() != null) {
            throw new InvalidOperationException("Cannot edit a deleted review");
        }

        if (requestDto.getRating() == null || requestDto.getRating() < 1 || requestDto.getRating() > 5) {
            throw new IllegalArgumentException("Rating is mandatory and must be between 1 and 5");
        }

        if (requestDto.getComment() != null && requestDto.getComment().length() > 1000) {
            throw new IllegalArgumentException("Comment must be less than 1000 characters");
        }

        int newCount = (newImages != null) ? newImages.size() : 0;
        int retainedCount = (retainedImages != null) ? retainedImages.size() : 0;
        if (newCount + retainedCount > 2) {
            throw new IllegalArgumentException("A review can have a maximum of 2 images");
        }

        // Validate any new images
        if (newImages != null) {
            for (MultipartFile img : newImages) {
                validateImage(img);
            }
        }

        // Handle image replacement/cleanup
        List<String> currentImages = new ArrayList<>();
        if (review.getPhotoUrl1() != null) currentImages.add(review.getPhotoUrl1());
        if (review.getPhotoUrl2() != null) currentImages.add(review.getPhotoUrl2());

        List<String> imagesToDelete = currentImages.stream()
                .filter(img -> retainedImages == null || !retainedImages.contains(img))
                .collect(Collectors.toList());

        // Upload new images
        List<String> finalUrls = new ArrayList<>(retainedImages != null ? retainedImages : Collections.emptyList());
        if (newImages != null && !newImages.isEmpty()) {
            try {
                List<String> uploaded = cloudinaryService.uploadImages(newImages, "lemon-house/reviews");
                finalUrls.addAll(uploaded);
            } catch (IOException e) {
                throw new RuntimeException("Cloudinary image upload failed: " + e.getMessage(), e);
            }
        }

        // Clean up orphaned images from Cloudinary
        if (!imagesToDelete.isEmpty()) {
            cloudinaryService.deleteImages(imagesToDelete);
        }

        review.setRating(requestDto.getRating());
        review.setComment(requestDto.getComment());
        review.setPhotoUrl1(finalUrls.size() > 0 ? finalUrls.get(0) : null);
        review.setPhotoUrl2(finalUrls.size() > 1 ? finalUrls.get(1) : null);

        ProductReview saved = reviewRepository.save(review);
        evictCache();

        return convertToDto(saved);
    }

    @Override
    @Transactional
    public void deleteReview(UUID reviewId, UUID userId, boolean isAdmin) {
        ProductReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));

        if (!isAdmin && !review.getUser().getId().equals(userId)) {
            throw new UnauthorizedAccessException("You are not authorized to delete this review");
        }

        // Clean up images from Cloudinary
        List<String> imagesToDelete = new ArrayList<>();
        if (review.getPhotoUrl1() != null) imagesToDelete.add(review.getPhotoUrl1());
        if (review.getPhotoUrl2() != null) imagesToDelete.add(review.getPhotoUrl2());
        if (!imagesToDelete.isEmpty()) {
            cloudinaryService.deleteImages(imagesToDelete);
        }

        // Clear references
        review.setPhotoUrl1(null);
        review.setPhotoUrl2(null);

        // Soft delete
        review.setDeletedAt(LocalDateTime.now());
        reviewRepository.save(review);
        evictCache();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<ProductReviewResponseDto> getProductReviews(UUID productId, Pageable pageable) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found");
        }
        Page<ProductReview> reviewPage = reviewRepository.findByProductIdAndDeletedAtIsNull(productId, pageable);
        return PageResponseDto.of(reviewPage, this::convertToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductReviewResponseDto getMyReview(UUID productId, UUID userId) {
        ProductReview review = reviewRepository.findByProductIdAndUserIdAndDeletedAtIsNull(productId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found for this product by current user"));
        return convertToDto(review);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductRatingSummaryDto getProductRatingSummary(UUID productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found");
        }

        Double avg = reviewRepository.getAverageRatingByProductId(productId);
        Long count = reviewRepository.getReviewCountByProductId(productId);
        List<Object[]> distributionRaw = reviewRepository.getRatingDistributionByProductId(productId);

        Map<Integer, Long> distribution = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            distribution.put(i, 0L);
        }

        if (distributionRaw != null) {
            for (Object[] row : distributionRaw) {
                Integer rating = (Integer) row[0];
                Long cnt = (Long) row[1];
                if (rating != null) {
                    distribution.put(rating, cnt);
                }
            }
        }

        return ProductRatingSummaryDto.builder()
                .averageRating(avg != null ? Math.round(avg * 10.0) / 10.0 : null)
                .reviewCount(count != null ? count : 0L)
                .ratingDistribution(distribution)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<ProductReviewResponseDto> searchAndFilterReviews(String query, Integer rating, UUID productId, Pageable pageable) {
        Page<ProductReview> reviewPage = reviewRepository.searchAndFilterReviews(query, rating, productId, pageable);
        return PageResponseDto.of(reviewPage, this::convertToDto);
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) return;
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Allowed image formats: JPG, JPEG, PNG, WEBP");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException("Image file size must be less than 5MB");
        }
    }

    private void evictCache() {
        try {
            redisService.deletePattern("products:category:*");
        } catch (Exception e) {
            // Log & ignore cache failures
        }
    }

    private ProductReviewResponseDto convertToDto(ProductReview review) {
        ProductReviewResponseDto.ReviewerDto reviewerDto = ProductReviewResponseDto.ReviewerDto.builder()
                .id(review.getUser().getId())
                .name(review.getUser().getName())
                .email(review.getUser().getEmail())
                .profileImage(review.getUser().getProfileImage())
                .build();

        return ProductReviewResponseDto.builder()
                .id(review.getId())
                .productId(review.getProduct().getId())
                .productName(review.getProduct().getName())
                .user(reviewerDto)
                .rating(review.getRating())
                .comment(review.getComment())
                .photoUrl1(review.getPhotoUrl1())
                .photoUrl2(review.getPhotoUrl2())
                .verifiedPurchase(review.getVerifiedPurchase())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
