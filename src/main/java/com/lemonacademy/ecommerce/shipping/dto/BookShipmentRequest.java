package com.lemonacademy.ecommerce.shipping.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookShipmentRequest {
    @NotBlank(message = "Order ID is required")
    private String orderId;

    private String pickupAddressId;

    public BookShipmentRequest(String orderId) {
        this.orderId = orderId;
    }
}
