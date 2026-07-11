package com.lemonacademy.ecommerce.shipping.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerEstimateRequest {
    @NotBlank(message = "Destination pincode is required")
    private String destinationPincode;

    private Integer weight; // in grams (optional, defaults to config)
    private BigDecimal orderValue; // optional, for COD/prepaid estimate
}
