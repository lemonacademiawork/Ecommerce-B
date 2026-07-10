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
        when(userRepository.count()).thenReturn(10L);
        when(productRepository.count()).thenReturn(25L);
        when(orderRepository.count()).thenReturn(50L);
        when(orderRepository.countByStatus(OrderStatus.PENDING)).thenReturn(5L);
        when(orderRepository.countByStatus(OrderStatus.DELIVERED)).thenReturn(30L);
        when(orderRepository.countByStatus(OrderStatus.CANCELLED)).thenReturn(3L);

        DashboardResponse response = dashboardService.getDashboardStatistics();

        assertThat(response).isNotNull();
        assertThat(response.getTotalUsers()).isEqualTo(10L);
        assertThat(response.getTotalProducts()).isEqualTo(25L);
        assertThat(response.getTotalOrders()).isEqualTo(50L);
        assertThat(response.getPendingOrders()).isEqualTo(5L);
        assertThat(response.getDeliveredOrders()).isEqualTo(30L);
        assertThat(response.getCancelledOrders()).isEqualTo(3L);
    }
}
