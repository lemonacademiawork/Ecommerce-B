package com.lemonacademy.ecommerce.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartResponse {

    private UUID cartId;
    private List<CartItemResponse> items;
    private BigDecimal totalAmount;
    private Integer totalItems;
}
