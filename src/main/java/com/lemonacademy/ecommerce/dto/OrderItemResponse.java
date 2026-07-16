package com.lemonacademy.ecommerce.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemResponse {

    private UUID id;
    private UUID productId;
    private String productName;
    private String imageUrl;
    private java.util.List<String> imageUrls;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal subtotal;
}
