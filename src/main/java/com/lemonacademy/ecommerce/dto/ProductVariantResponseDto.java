package com.lemonacademy.ecommerce.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariantResponseDto {
    private UUID id;
    private UUID productId;
    private String variantName;
    private Integer weight;
    private String weightUnit;
    private Integer volume;
    private String volumeUnit;
    private String sizeLabel;
    private BigDecimal price;
    private BigDecimal discountedPrice;
    private Integer stock;
    private String sku;
    private String barcode;
    private Boolean status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
