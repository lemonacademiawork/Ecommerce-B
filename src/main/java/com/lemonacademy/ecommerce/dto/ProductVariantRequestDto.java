package com.lemonacademy.ecommerce.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariantRequestDto {

    @NotBlank(message = "Variant name is required")
    private String variantName;

    private Integer weight;
    private String weightUnit;
    
    private Integer volume;
    private String volumeUnit;
    
    private String sizeLabel;

    @NotNull(message = "Variant price is required")
    @Min(value = 0, message = "Price must be greater than or equal to 0")
    private BigDecimal price;

    private BigDecimal discountedPrice;

    @NotNull(message = "Variant stock is required")
    @Min(value = 0, message = "Stock must be greater than or equal to 0")
    private Integer stock;

    private String sku;
    private String barcode;
    
    @Builder.Default
    private Boolean status = true;
}
