package com.lemonacademy.ecommerce.shipping.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CancelShipmentRequest {
    @NotNull(message = "Order ID is required")
    private UUID orderId;
}
