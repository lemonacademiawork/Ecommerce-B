package com.lemonacademy.ecommerce.repository;

import java.util.UUID;
import java.math.BigDecimal;
import java.util.List;

import com.lemonacademy.ecommerce.dto.CategoryStockDto;
import com.lemonacademy.ecommerce.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    Page<Product> findAllByActiveTrue(Pageable pageable);
    Page<Product> findAllByCategoryId(UUID categoryId, Pageable pageable);
    Page<Product> findAllByCategoryIdAndActiveTrue(UUID categoryId, Pageable pageable);
    Page<Product> findAllByPriceBetweenAndActiveTrue(BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);
    Page<Product> findAllByCategoryIdIn(List<UUID> categoryIds, Pageable pageable);
    Page<Product> findAllByCategoryIdInAndActiveTrue(List<UUID> categoryIds, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true AND (p.category IS NULL OR LOWER(p.category.name) != LOWER(:categoryName))")
    Page<Product> findAllActiveExcludingCategory(@Param("categoryName") String categoryName, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true AND p.category IS NOT NULL AND LOWER(p.category.name) = LOWER(:categoryName)")
    Page<Product> findAllActiveByCategoryName(@Param("categoryName") String categoryName, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE (LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Product> searchProducts(@Param("query") String query, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE (LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%'))) AND p.active = true")
    Page<Product> searchActiveProducts(@Param("query") String query, Pageable pageable);

    // Stock status query methods
    @Query("SELECT p FROM Product p WHERE (p.stock IS NULL OR p.stock <= 0)")
    Page<Product> findOutOfStockProducts(Pageable pageable);

    @Query("SELECT p FROM Product p WHERE (p.stock IS NULL OR p.stock <= 0) AND p.active = true")
    Page<Product> findActiveOutOfStockProducts(Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.stock > 0 AND p.stock < 5")
    Page<Product> findLowStockProducts(Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.stock > 0 AND p.stock < 5 AND p.active = true")
    Page<Product> findActiveLowStockProducts(Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.stock >= 5")
    Page<Product> findInStockProducts(Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.stock >= 5 AND p.active = true")
    Page<Product> findActiveInStockProducts(Pageable pageable);

    // Stock count methods for Dashboard
    @Query("SELECT COUNT(p) FROM Product p WHERE (p.stock IS NULL OR p.stock <= 0)")
    long countOutOfStock();

    @Query("SELECT COUNT(p) FROM Product p WHERE p.stock > 0 AND p.stock < 5")
    long countLowStock();

    @Query("SELECT COUNT(p) FROM Product p WHERE p.stock >= 5")
    long countInStock();

    // Category stock distribution aggregation for Dashboard Donut Chart
    @Query("SELECT new com.lemonacademy.ecommerce.dto.CategoryStockDto(COALESCE(c.name, 'Uncategorized'), COUNT(p)) " +
           "FROM Product p LEFT JOIN p.category c GROUP BY c.name")
    List<CategoryStockDto> findCategoryStockDistribution();
}
