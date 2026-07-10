package com.lemonacademy.ecommerce.dto;

import com.lemonacademy.ecommerce.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.lemonacademy.ecommerce.shipping.dto.TrackingResponse.TrackingEvent;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {

    private Long id;
    private Long userId;
    private AddressResponse address;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private List<OrderItemResponse> items;
    private LocalDateTime createdAt;

    // Shipping Fields
    private String shipmentId;
    private String trackingNumber;
    private String awbNumber;
    private String courierName;
    private String shipmentStatus;
    private BigDecimal shippingCharge;
    private LocalDateTime estimatedDeliveryDate;
    private String labelUrl;
    private Boolean pickupRequested;
    private LocalDateTime pickupDate;
    private String reverseShipmentId;
    private LocalDateTime lastTrackingSync;

    // Additional Shipment Details requested by frontend
    private Integer weight;
    private Integer length;
    private Integer breadth;
    private Integer height;
    private List<TrackingEvent> trackingEvents;
}
