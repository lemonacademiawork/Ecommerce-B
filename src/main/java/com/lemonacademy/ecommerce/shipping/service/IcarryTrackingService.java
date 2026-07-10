package com.lemonacademy.ecommerce.shipping.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemonacademy.ecommerce.entity.Order;
import com.lemonacademy.ecommerce.repository.OrderRepository;
import com.lemonacademy.ecommerce.shipping.client.IcarryClient;
import com.lemonacademy.ecommerce.shipping.dto.TrackingResponse;
import com.lemonacademy.ecommerce.shipping.exception.IcarryApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class IcarryTrackingService {

    private final IcarryClient client;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    public IcarryTrackingService(IcarryClient client, OrderRepository orderRepository, ObjectMapper objectMapper) {
        this.client = client;
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
    }

    public TrackingResponse trackShipment(String trackingNumber) {
        log.info("Requesting tracking details from iCarry for AWB: {}", trackingNumber);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("awb", trackingNumber);

        try {
            // Tracking API is safe to retry
            String responseBody = client.post("/api_track_shipment", body, true);
            JsonNode root = objectMapper.readTree(responseBody);

            if (root.has("error")) {
                throw new IcarryApiException("Tracking request failed: " + root.get("error").toString());
            }

            String status = root.has("status") ? root.get("status").asText() : "UNKNOWN";
            String courier = root.has("courier") ? root.get("courier").asText() : "Standard Courier";
            String shipmentId = root.has("shipment_id") ? root.get("shipment_id").asText() : "";

            List<TrackingResponse.TrackingEvent> events = new ArrayList<>();
            if (root.has("history") && root.get("history").isArray()) {
                for (JsonNode eventNode : root.get("history")) {
                    events.add(TrackingResponse.TrackingEvent.builder()
                            .timestamp(eventNode.has("time") ? eventNode.get("time").asText() : "")
                            .location(eventNode.has("location") ? eventNode.get("location").asText() : "")
                            .activity(eventNode.has("activity") ? eventNode.get("activity").asText() : "")
                            .build());
                }
            }

            return TrackingResponse.builder()
                    .awbNumber(trackingNumber)
                    .shipmentId(shipmentId)
                    .status(status)
                    .courierName(courier)
                    .events(events)
                    .build();
        } catch (IcarryApiException e) {
            log.error("Failed to track AWB {}: {}", trackingNumber, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error tracking AWB {}: {}", trackingNumber, e.getMessage());
            throw new IcarryApiException("Tracking API failed: " + e.getMessage(), 500, e);
        }
    }

    @Transactional
    public Order syncTrackingStatus(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with ID: " + orderId));

        if (order.getAwbNumber() == null || order.getAwbNumber().isEmpty()) {
            throw new IcarryApiException("No tracking AWB number associated with this order.");
        }

        log.info("Syncing tracking status for order ID: {}, AWB: {}", order.getId(), order.getAwbNumber());
        TrackingResponse tracking = trackShipment(order.getAwbNumber());

        // Update database attributes
        order.setShipmentStatus(tracking.getStatus());
        order.setLastTrackingSync(LocalDateTime.now());

        if (rootStatusNeedsUpdate(tracking.getStatus(), order)) {
            updateOrderStatusFromShipment(tracking.getStatus(), order);
        }

        return orderRepository.save(order);
    }

    private boolean rootStatusNeedsUpdate(String shipmentStatus, Order order) {
        return shipmentStatus != null;
    }

    private void updateOrderStatusFromShipment(String shipmentStatus, Order order) {
        String status = shipmentStatus.toUpperCase();
        if (status.contains("DELIVERED") || status.contains("COMPLETED")) {
            order.setStatus(com.lemonacademy.ecommerce.entity.OrderStatus.DELIVERED);
        } else if (status.contains("SHIPPED") || status.contains("TRANSIT") || status.contains("DISPATCHED")) {
            order.setStatus(com.lemonacademy.ecommerce.entity.OrderStatus.SHIPPED);
        } else if (status.contains("CANCEL") || status.contains("VOID")) {
            order.setStatus(com.lemonacademy.ecommerce.entity.OrderStatus.CANCELLED);
        } else if (status.contains("PICK") || status.contains("BOOK")) {
            order.setStatus(com.lemonacademy.ecommerce.entity.OrderStatus.PROCESSING);
        }
    }
}
