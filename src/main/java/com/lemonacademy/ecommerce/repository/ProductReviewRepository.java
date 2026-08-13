package com.lemonacademy.ecommerce.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.lemonacademy.ecommerce.entity.ProductReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReview, UUID> {

    Page<ProductReview> findByProductIdAndDeletedAtIsNull(UUID productId, Pageable pageable);

    Optional<ProductReview> findByProductIdAndUserIdAndDeletedAtIsNull(UUID productId, UUID userId);

    boolean existsByProductIdAndUserIdAndDeletedAtIsNull(UUID productId, UUID userId);

    @Query("SELECT AVG(pr.rating) FROM ProductReview pr WHERE pr.product.id = :productId AND pr.deletedAt IS NULL")
    Double getAverageRatingByProductId(@Param("productId") UUID productId);

    @Query("SELECT COUNT(pr) FROM ProductReview pr WHERE pr.product.id = :productId AND pr.deletedAt IS NULL")
    Long getReviewCountByProductId(@Param("productId") UUID productId);

    @Query("SELECT pr.rating, COUNT(pr) FROM ProductReview pr WHERE pr.product.id = :productId AND pr.deletedAt IS NULL GROUP BY pr.rating")
    List<Object[]> getRatingDistributionByProductId(@Param("productId") UUID productId);
    
    // For admin searching and filtering
    @Query("SELECT pr FROM ProductReview pr WHERE pr.deletedAt IS NULL")
    Page<ProductReview> findAllActiveReviews(Pageable pageable);

    @Query("SELECT pr FROM ProductReview pr JOIN pr.user u JOIN pr.product p WHERE " +
           "pr.deletedAt IS NULL AND " +
           "(:rating IS NULL OR pr.rating = :rating) AND " +
           "(:productId IS NULL OR pr.product.id = :productId) AND " +
           "(:query IS NULL OR :query = '' OR " +
           "LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(pr.comment) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<ProductReview> searchAndFilterReviews(
            @Param("query") String query,
            @Param("rating") Integer rating,
            @Param("productId") UUID productId,
            Pageable pageable);
}
