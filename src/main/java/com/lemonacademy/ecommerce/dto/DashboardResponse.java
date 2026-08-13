package com.lemonacademy.ecommerce.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardResponse {
    private long totalUsers;
    private long totalProducts;
    private long totalOrders;
    private long pendingOrders;
    private long deliveredOrders;
    private long cancelledOrders;
    private long inStockProducts;
    private long outOfStockProducts;
    private long lowStockProducts;
    private BigDecimal totalRevenue;
    private List<CategoryStockDto> stockByCategory;
    private List<MonthlyAnalyticsDto> monthlyAnalytics;
}
