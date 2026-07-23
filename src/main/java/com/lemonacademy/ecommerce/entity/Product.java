package com.lemonacademy.ecommerce.entity;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(length = 5000)
    private String description;

    @Column(nullable = true)
    private BigDecimal price;

    @Column(nullable = true)
    private Integer stock;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "image_url")
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductVariant> variants = new ArrayList<>();

    @Builder.Default
    private Boolean hasVariants = false;

    @Transient
    public String getImageUrl() {
        return (imageUrls != null && !imageUrls.isEmpty()) ? imageUrls.get(0) : null;
    }

    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // Physical dimensions for shipping
    private Integer weight; // in grams
    private Integer length; // in cm
    private Integer breadth; // in cm
    private Integer height; // in cm

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