package com.lemonacademy.ecommerce.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderRequest {

    @NotNull(message = "Address ID is required")
    private UUID addressId;

    private String couponCode;

    /**
     * Optional list of items to order. If provided and the backend cart is empty,
     * these items will be used to create the order directly.
     * If omitted, the backend falls back to the user's database cart.
     */
    @Valid
    private List<OrderItemRequest> items;
}
