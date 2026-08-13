package com.lemonacademy.ecommerce.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyAnalyticsDto {
    private String month;
    private int monthNumber;
    private BigDecimal revenue;
    private long orderCount;
}
