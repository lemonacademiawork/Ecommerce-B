package com.lemonacademy.ecommerce.entity;

import java.util.UUID;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id", nullable = false)
    private Address address;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "shipment_id")
    private String shipmentId;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "awb_number")
    private String awbNumber;

    @Column(name = "courier_name")
    private String courierName;

    @Column(name = "shipment_status")
    private String shipmentStatus;

    @Column(name = "shipping_charge")
    private BigDecimal shippingCharge;

    @Column(name = "estimated_delivery_date")
    private LocalDateTime estimatedDeliveryDate;

    @Column(name = "label_url")
    private String labelUrl;

    @Column(name = "pickup_requested")
    @Builder.Default
    private Boolean pickupRequested = false;

    @Column(name = "pickup_date")
    private LocalDateTime pickupDate;

    @Column(name = "reverse_shipment_id")
    private String reverseShipmentId;

    @Column(name = "delivery_attempts")
    @Builder.Default
    private Integer deliveryAttempts = 0;

    @Column(name = "last_tracking_sync")
    private LocalDateTime lastTrackingSync;

    @Column(name = "weight")
    private Integer weight;

    @Column(name = "length")
    private Integer length;

    @Column(name = "breadth")
    private Integer breadth;

    @Column(name = "height")
    private Integer height;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.pickupRequested == null) {
            this.pickupRequested = false;
        }
        if (this.deliveryAttempts == null) {
            this.deliveryAttempts = 0;
        }
    }
}
