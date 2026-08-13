package com.lemonacademy.ecommerce.dto;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRatingSummaryDto {
    private Double averageRating;
    private Long reviewCount;
    private Map<Integer, Long> ratingDistribution;
}
