package com.lemonacademy.ecommerce.shipping.service;

import com.lemonacademy.ecommerce.entity.Order;
import com.lemonacademy.ecommerce.entity.OrderStatus;
import com.lemonacademy.ecommerce.repository.OrderRepository;
import com.lemonacademy.ecommerce.shipping.config.IcarryConfig;
import com.lemonacademy.ecommerce.shipping.dto.IcarryWebhookPayload;
import com.lemonacademy.ecommerce.exception.UnauthorizedAccessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
public class IcarryWebhookService {

    private final OrderRepository orderRepository;
    private final IcarryConfig config;

    public IcarryWebhookService(OrderRepository orderRepository, IcarryConfig config) {
        this.orderRepository = orderRepository;
        this.config = config;
    }

    @Transactional
    public void processWebhook(IcarryWebhookPayload payload) {
        log.info("Received iCarry Webhook. AWB: {}, Status code: {}", payload.getAwb(), payload.getStatus());

        // 1. Verify token
        if (payload.getToken() == null || !payload.getToken().equals(config.getApiKey())) {
            log.error("Invalid token in webhook request: {}. Expected: {}", payload.getToken(), config.getApiKey());
            throw new UnauthorizedAccessException("Webhook authentication failed: Invalid token.");
        }

        // 2. Fetch Order by AWB
        Order order = orderRepository.findByAwbNumber(payload.getAwb())
                .orElseGet(() -> orderRepository.findByShipmentId(payload.getAwb())
                .orElseThrow(() -> new RuntimeException("No order found with AWB or shipment ID: " + payload.getAwb())));

        // 3. Map status code to statuses
        String mappedShipmentStatus = mapShipmentStatusCode(payload.getStatus());
        OrderStatus mappedOrderStatus = mapOrderStatusCode(payload.getStatus());

        log.info("Updating order ID: {} via Webhook. ShipmentStatus -> {}, OrderStatus -> {}", 
                 order.getId(), mappedShipmentStatus, mappedOrderStatus);

        order.setShipmentStatus(mappedShipmentStatus);
        order.setStatus(mappedOrderStatus);
        order.setLastTrackingSync(LocalDateTime.now());

        if ("DELIVERED".equals(mappedShipmentStatus)) {
            order.setDeliveryAttempts(order.getDeliveryAttempts() + 1);
        }

        orderRepository.save(order);
    }

    private String mapShipmentStatusCode(Integer statusCode) {
        if (statusCode == null) return "UNKNOWN";
        switch (statusCode) {
            case 1:
            case 25:
                return "PENDING_PICKUP";
            case 2:
            case 24:
                return "PROCESSING";
            case 3:
                return "SHIPPED";
            case 22:
                return "IN_TRANSIT";
            case 26:
                return "OUT_FOR_DELIVERY";
            case 21:
                return "DELIVERED";
            case 7:
            case 16:
                return "CANCELLED";
            case 23:
            case 27:
                return "RETURNED";
            case 12:
            case 14:
                return "FAILED_DELIVERY";
            default:
                return "STATUS_" + statusCode;
        }
    }

    private OrderStatus mapOrderStatusCode(Integer statusCode) {
        if (statusCode == null) return OrderStatus.PROCESSING;
        switch (statusCode) {
            case 21: // Delivered
                return OrderStatus.DELIVERED;
            case 7:
            case 16: // Cancelled / Voided
                return OrderStatus.CANCELLED;
            case 3:
            case 22:
            case 26: // Shipped, In Transit, Out for Delivery
                return OrderStatus.SHIPPED;
            default:
                return OrderStatus.PROCESSING;
        }
    }
}
