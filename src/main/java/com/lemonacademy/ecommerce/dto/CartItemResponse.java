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
public class CartItemResponse {

    private UUID cartItemId;
    private UUID productId;
    private String productName;
    private String imageUrl;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal subtotal;
}
