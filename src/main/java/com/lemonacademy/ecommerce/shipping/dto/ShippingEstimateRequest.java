package com.lemonacademy.ecommerce.shipping.dto;

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
public class ShippingEstimateRequest {
    @NotNull(message = "Weight is required")
    private Integer weight; // in grams

    private Integer length; // in cm
    private Integer breadth; // in cm
    private Integer height; // in cm

    @NotBlank(message = "Origin pincode is required")
    private String originPincode;

    @NotBlank(message = "Destination pincode is required")
    private String destinationPincode;

    private String originCountryCode; // default IN
    private String destinationCountryCode; // default IN

    private String shipmentMode; // E = Express, S = Surface, H = Hyperlocal

    private String parcelType; // P = Prepaid, C = COD, R = Reverse
    private java.math.BigDecimal parcelValue;
}
