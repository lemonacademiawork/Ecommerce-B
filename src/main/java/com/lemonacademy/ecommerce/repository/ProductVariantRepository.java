package com.lemonacademy.ecommerce.repository;

import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.lemonacademy.ecommerce.entity.ProductVariant;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {
    List<ProductVariant> findByProductId(UUID productId);
    List<ProductVariant> findByProductIdAndStatusTrue(UUID productId);
}
