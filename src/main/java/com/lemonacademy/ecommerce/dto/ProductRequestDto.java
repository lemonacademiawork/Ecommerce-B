package com.lemonacademy.ecommerce.dto;

import java.util.UUID;
import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRequestDto {

    @NotBlank(message = "Product name is required")
    private String name;

    private String description;

    @Min(value = 0, message = "Price must be greater than or equal to 0")
    private BigDecimal price;

    @Min(value = 0, message = "Stock must be greater than or equal to 0")
    private Integer stock;

    private String imageUrl;
    
    private List<String> imageUrls;
    
    private List<String> existingImageUrls;

    private Boolean active;
    
    private Boolean hasVariants;

    @NotNull(message = "Category ID is required")
    private UUID categoryId;

    private Double weight;
    private Double length;
    private Double breadth;
    private Double height;

    public Integer getWeightInt() {
        return weight != null ? (int) Math.round(weight) : null;
    }

    public Integer getLengthInt() {
        return length != null ? (int) Math.round(length) : null;
    }

    public Integer getBreadthInt() {
        return breadth != null ? (int) Math.round(breadth) : null;
    }

    public Integer getHeightInt() {
        return height != null ? (int) Math.round(height) : null;
    }
}
