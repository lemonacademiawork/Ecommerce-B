package com.lemonacademy.ecommerce.service;

import com.lemonacademy.ecommerce.dto.DashboardResponse;
import com.lemonacademy.ecommerce.entity.OrderStatus;
import com.lemonacademy.ecommerce.repository.OrderRepository;
import com.lemonacademy.ecommerce.repository.ProductRepository;
import com.lemonacademy.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    void getDashboardStatistics_Success() {
        when(userRepository.count()).thenReturn(100L);
        when(productRepository.count()).thenReturn(50L);
        when(orderRepository.count()).thenReturn(200L);
        when(orderRepository.countByStatus(OrderStatus.PENDING)).thenReturn(20L);
        when(orderRepository.countByStatus(OrderStatus.DELIVERED)).thenReturn(150L);
        when(orderRepository.countByStatus(OrderStatus.CANCELLED)).thenReturn(30L);

        DashboardResponse response = dashboardService.getDashboardStatistics();

        assertThat(response).isNotNull();
        assertThat(response.getTotalUsers()).isEqualTo(100L);
        assertThat(response.getTotalProducts()).isEqualTo(50L);
        assertThat(response.getTotalOrders()).isEqualTo(200L);
        assertThat(response.getPendingOrders()).isEqualTo(20L);
        assertThat(response.getDeliveredOrders()).isEqualTo(150L);
        assertThat(response.getCancelledOrders()).isEqualTo(30L);
    }
}
