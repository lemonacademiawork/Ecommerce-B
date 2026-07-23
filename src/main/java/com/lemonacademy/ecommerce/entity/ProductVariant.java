package com.lemonacademy.ecommerce.entity;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "product_variants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private String variantName;

    private Integer weight;
    private String weightUnit;
    
    private Integer volume;
    private String volumeUnit;
    
    private String sizeLabel;

    @Column(nullable = false)
    private BigDecimal price;

    private BigDecimal discountedPrice;

    @Column(nullable = false)
    private Integer stock;

    private String sku;
    private String barcode;

    @Builder.Default
    private Boolean status = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
