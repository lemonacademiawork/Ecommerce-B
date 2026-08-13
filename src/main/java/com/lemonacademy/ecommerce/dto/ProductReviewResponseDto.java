package com.lemonacademy.ecommerce.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductReviewResponseDto {

    private UUID id;
    private UUID productId;
    private String productName;
    private ReviewerDto user;
    private Integer rating;
    private String comment;
    private String photoUrl1;
    private String photoUrl2;
    private Boolean verifiedPurchase;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewerDto {
        private UUID id;
        private String name;
        private String email;
        private String profileImage;
    }
}
