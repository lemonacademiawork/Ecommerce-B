package com.lemonacademy.ecommerce.repository;

import java.util.UUID;

import java.util.UUID;
import java.math.BigDecimal;
import java.util.List;

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

    @Query("SELECT p FROM Product p WHERE (LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Product> searchProducts(@Param("query") String query, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE (LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%'))) AND p.active = true")
    Page<Product> searchActiveProducts(@Param("query") String query, Pageable pageable);
}
