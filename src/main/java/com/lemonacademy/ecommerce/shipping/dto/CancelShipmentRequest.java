package com.lemonacademy.ecommerce.shipping.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CancelShipmentRequest {
    @NotBlank(message = "Order ID is required")
    private String orderId;
}
