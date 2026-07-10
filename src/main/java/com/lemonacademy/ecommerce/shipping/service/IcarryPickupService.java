package com.lemonacademy.ecommerce.shipping.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemonacademy.ecommerce.entity.Order;
import com.lemonacademy.ecommerce.repository.OrderRepository;
import com.lemonacademy.ecommerce.shipping.client.IcarryClient;
import com.lemonacademy.ecommerce.shipping.dto.PickupAddressRequest;
import com.lemonacademy.ecommerce.shipping.exception.IcarryApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.LocalDateTime;

@Service
@Slf4j
public class IcarryPickupService {

    private final IcarryClient client;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    public IcarryPickupService(IcarryClient client, OrderRepository orderRepository, ObjectMapper objectMapper) {
        this.client = client;
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
    }

    public String createOrUpdatePickupAddress(PickupAddressRequest request) {
        log.info("Creating/updating pickup address with contact: {}", request.getContactPerson());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        if (request.getId() != null) {
            body.add("pickup_address_id", request.getId());
        }
        body.add("contact_person", request.getContactPerson());
        body.add("phone", request.getPhone());
        body.add("address_line1", request.getAddressLine1());
        body.add("address_line2", request.getAddressLine2() != null ? request.getAddressLine2() : "");
        body.add("city", request.getCity());
        body.add("state", request.getState());
        body.add("pincode", request.getPincode());
        body.add("country", request.getCountry() != null ? request.getCountry() : "IN");

        try {
            // Address sync is safe to retry
            String responseBody = client.post("/api_add_pickup_address", body, true);
            JsonNode root = objectMapper.readTree(responseBody);

            if (root.has("error")) {
                throw new IcarryApiException("Pickup address management failed: " + root.get("error").toString());
            }

            String addressId = root.has("pickup_address_id") ? root.get("pickup_address_id").asText() : 
                              (root.has("id") ? root.get("id").asText() : "SUCCESS_ID");
            
            log.info("Pickup address successfully managed. Address ID: {}", addressId);
            return addressId;
        } catch (IcarryApiException e) {
            log.error("Failed to manage pickup address: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error managing pickup address: {}", e.getMessage());
            throw new IcarryApiException("Pickup Address API failed: " + e.getMessage(), 500, e);
        }
    }

    @Transactional
    public Order requestPickup(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with ID: " + orderId));

        if (order.getShipmentId() == null || order.getShipmentId().isEmpty()) {
            throw new IcarryApiException("Cannot request pickup. No shipment has been booked for order: " + orderId);
        }

        if (Boolean.TRUE.equals(order.getPickupRequested())) {
            log.warn("Pickup already requested for order ID: {}", orderId);
            return order;
        }

        log.info("Requesting package pickup slot for order ID: {}, Shipment ID: {}", order.getId(), order.getShipmentId());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("shipment_id", order.getShipmentId());
        body.add("awb", order.getAwbNumber());

        try {
            // Requesting pickup is safe to retry
            String responseBody = client.post("/api_request_pickup", body, true);
            JsonNode root = objectMapper.readTree(responseBody);

            if (root.has("error")) {
                throw new IcarryApiException("Pickup request failed: " + root.get("error").toString());
            }

            order.setPickupRequested(true);
            order.setPickupDate(LocalDateTime.now().plusDays(1)); // Scheduled for next day
            order.setLastTrackingSync(LocalDateTime.now());

            log.info("Pickup successfully scheduled for order ID: {}", orderId);
            return orderRepository.save(order);
        } catch (IcarryApiException e) {
            log.error("Failed to schedule pickup for order {}: {}", orderId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error scheduling pickup for order {}: {}", orderId, e.getMessage());
            throw new IcarryApiException("Pickup API failed: " + e.getMessage(), 500, e);
        }
    }
}
