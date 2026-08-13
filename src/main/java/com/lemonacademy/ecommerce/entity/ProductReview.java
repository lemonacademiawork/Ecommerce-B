package com.lemonacademy.ecommerce.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "product_reviews",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_product_reviews_user_product", columnNames = {"user_id", "product_id"})
    },
    indexes = {
        @Index(name = "idx_product_reviews_product_id", columnList = "product_id"),
        @Index(name = "idx_product_reviews_user_id", columnList = "user_id"),
        @Index(name = "idx_product_reviews_created_at", columnList = "created_at"),
        @Index(name = "idx_product_reviews_rating", columnList = "rating")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Integer rating;

    @Column(length = 1000)
    private String comment;

    @Column(name = "photo_url1")
    private String photoUrl1;

    @Column(name = "photo_url2")
    private String photoUrl2;

    @Column(name = "verified_purchase", nullable = false)
    @Builder.Default
    private Boolean verifiedPurchase = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.verifiedPurchase == null) {
            this.verifiedPurchase = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
