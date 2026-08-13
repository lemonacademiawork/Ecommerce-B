package com.lemonacademy.ecommerce.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemonacademy.ecommerce.dto.CategoryStockDto;
import com.lemonacademy.ecommerce.dto.DashboardResponse;
import com.lemonacademy.ecommerce.dto.MonthlyAnalyticsDto;
import com.lemonacademy.ecommerce.entity.Order;
import com.lemonacademy.ecommerce.entity.OrderStatus;
import com.lemonacademy.ecommerce.entity.PaymentStatus;
import com.lemonacademy.ecommerce.repository.OrderRepository;
import com.lemonacademy.ecommerce.repository.ProductRepository;
import com.lemonacademy.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UpstashRedisService redisService;
    private final ObjectMapper objectMapper;

    private static final String DASHBOARD_CACHE_KEY = "dashboard:metrics";
    private static final long CACHE_TTL_SECONDS = 60; // 60 seconds TTL for dashboard stats

    @Transactional(readOnly = true)
    public DashboardResponse getDashboardStatistics() {
        // Try reading from Redis cache first (< 20ms response time)
        try {
            String cachedJson = redisService.get(DASHBOARD_CACHE_KEY);
            if (cachedJson != null && !cachedJson.trim().isEmpty()) {
                log.debug("Dashboard metrics retrieved from Redis cache.");
                return objectMapper.readValue(cachedJson, DashboardResponse.class);
            }
        } catch (Exception e) {
            log.warn("Redis read error for dashboard metrics, falling back to DB query: {}", e.getMessage());
        }

        // Cache miss -> Fetch from Database
        long totalUsers = userRepository.count();
        long totalProducts = productRepository.count();
        long totalOrders = orderRepository.countByPaymentStatus(PaymentStatus.PAID);
        long pendingOrders = orderRepository.countByStatus(OrderStatus.PENDING);
        long deliveredOrders = orderRepository.countByStatus(OrderStatus.DELIVERED);
        long cancelledOrders = orderRepository.countByStatus(OrderStatus.CANCELLED);
        long outOfStockProducts = productRepository.countOutOfStock();
        long lowStockProducts = productRepository.countLowStock();
        long inStockProducts = productRepository.countInStock();

        BigDecimal totalRevenue = orderRepository.sumTotalAmountByPaymentStatus(PaymentStatus.PAID);
        if (totalRevenue == null) {
            totalRevenue = BigDecimal.ZERO;
        }

        // 1. Stock by Category Aggregation
        List<CategoryStockDto> stockByCategory = productRepository.findCategoryStockDistribution();

        // 2. Monthly Revenue & Orders Aggregation (Current Year Jan-Dec)
        List<MonthlyAnalyticsDto> monthlyAnalytics = buildMonthlyAnalytics();

        DashboardResponse response = DashboardResponse.builder()
                .totalUsers(totalUsers)
                .totalProducts(totalProducts)
                .totalOrders(totalOrders)
                .pendingOrders(pendingOrders)
                .deliveredOrders(deliveredOrders)
                .cancelledOrders(cancelledOrders)
                .inStockProducts(inStockProducts)
                .outOfStockProducts(outOfStockProducts)
                .lowStockProducts(lowStockProducts)
                .totalRevenue(totalRevenue)
                .stockByCategory(stockByCategory)
                .monthlyAnalytics(monthlyAnalytics)
                .build();

        // Write metrics to Redis cache asynchronously / safely
        try {
            String json = objectMapper.writeValueAsString(response);
            redisService.set(DASHBOARD_CACHE_KEY, json, CACHE_TTL_SECONDS);
        } catch (Exception e) {
            log.warn("Redis write error for dashboard metrics: {}", e.getMessage());
        }

        return response;
    }

    private List<MonthlyAnalyticsDto> buildMonthlyAnalytics() {
        int currentYear = LocalDate.now().getYear();
        LocalDateTime startOfYear = LocalDateTime.of(currentYear, 1, 1, 0, 0, 0);

        List<Order> yearOrders = orderRepository.findAllByCreatedAtGreaterThanEqual(startOfYear);

        Map<Integer, List<Order>> ordersByMonth = yearOrders.stream()
                .filter(o -> o.getCreatedAt() != null)
                .collect(Collectors.groupingBy(o -> o.getCreatedAt().getMonthValue()));

        List<MonthlyAnalyticsDto> monthlyList = new ArrayList<>();

        for (int m = 1; m <= 12; m++) {
            List<Order> monthOrders = ordersByMonth.getOrDefault(m, Collections.emptyList());

            BigDecimal monthRevenue = monthOrders.stream()
                    .filter(o -> o.getPaymentStatus() == PaymentStatus.PAID)
                    .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            long orderCount = monthOrders.size();
            String monthName = Month.of(m).getDisplayName(TextStyle.SHORT, Locale.ENGLISH);

            monthlyList.add(MonthlyAnalyticsDto.builder()
                    .month(monthName)
                    .monthNumber(m)
                    .revenue(monthRevenue)
                    .orderCount(orderCount)
                    .build());
        }

        return monthlyList;
    }
}
