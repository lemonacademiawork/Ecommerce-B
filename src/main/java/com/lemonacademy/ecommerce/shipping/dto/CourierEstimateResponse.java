package com.lemonacademy.ecommerce.shipping.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourierEstimateResponse {
    private String courierId;
    private String courierName;
    private String courierGroupName;
    private BigDecimal rate;
    private String eta;
}
