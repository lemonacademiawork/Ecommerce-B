package com.lemonacademy.ecommerce.shipping.service;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemonacademy.ecommerce.entity.Address;
import com.lemonacademy.ecommerce.entity.Order;
import com.lemonacademy.ecommerce.repository.OrderRepository;
import com.lemonacademy.ecommerce.shipping.client.IcarryClient;
import com.lemonacademy.ecommerce.shipping.config.IcarryConfig;
import com.lemonacademy.ecommerce.shipping.exception.DuplicateShipmentException;
import com.lemonacademy.ecommerce.shipping.exception.IcarryApiException;
import com.lemonacademy.ecommerce.shipping.exception.ShipmentCancelledException;
import com.lemonacademy.ecommerce.shipping.util.DimensionParser;
import com.lemonacademy.ecommerce.service.UpstashRedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
@Slf4j
public class IcarryShipmentService {

    private final IcarryClient client;
    private final IcarryConfig config;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;
    private final UpstashRedisService redisService;

    @org.springframework.beans.factory.annotation.Autowired
    public IcarryShipmentService(IcarryClient client, IcarryConfig config, 
                                 OrderRepository orderRepository, ObjectMapper objectMapper,
                                 @org.springframework.beans.factory.annotation.Autowired(required = false) UpstashRedisService redisService) {
        this.client = client;
        this.config = config;
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
        this.redisService = redisService;
    }

    @Transactional
    public Order bookShipmentForOrder(Order order) {
        return bookShipmentForOrder(order, null);
    }

    @Transactional
    public Order bookShipmentForOrder(Order order, String customPickupAddressId) {
        if (order.getShipmentId() != null) {
            log.warn("Shipment already booked for order ID: {}. Shipment ID: {}", order.getId(), order.getShipmentId());
            throw new DuplicateShipmentException("Shipment is already booked for this order: " + order.getShipmentId());
        }

        log.info("Booking shipment with iCarry for order ID: {}", order.getId());

        Address addr = order.getAddress();
        String contents = order.getItems().stream()
                .map(item -> {
                    String name = item.getProduct().getName();
                    if (name == null) name = "Item";
                    name = name.replaceAll("[^\\x20-\\x7E]", "").trim();
                    if (name.isEmpty()) name = "Item";
                    return name + " x" + item.getQuantity();
                })
                .collect(Collectors.joining(", "));
        if (contents.trim().length() < 3) {
            contents = "E-Commerce Parcel Items";
        }
        if (contents.length() > 200) {
            contents = contents.substring(0, 197) + "...";
        }

        // Determine dimensions
        int[] dims = DimensionParser.parse(config.getDefaultDimensions(), 10);
        int length = order.getLength() != null ? order.getLength() : dims[0];
        int breadth = order.getBreadth() != null ? order.getBreadth() : dims[1];
        int height = order.getHeight() != null ? order.getHeight() : dims[2];
        int weight = order.getWeight() != null ? order.getWeight() : config.getDefaultWeight();
        
        // Save dimensions to the order so they can be retrieved later
        order.setLength(length);
        order.setBreadth(breadth);
        order.setHeight(height);
        order.setWeight(weight);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        
        // 1. Consignee Details (Form bracket notation format)
        body.add("consignee[name]", addr.getFullName());
        body.add("consignee[mobile]", cleanMobileNumber(addr.getPhone()));
        body.add("consignee[address]", addr.getAddressLine1() + " " + (addr.getAddressLine2() != null ? addr.getAddressLine2() : ""));
        body.add("consignee[city]", addr.getCity());
        body.add("consignee[pincode]", addr.getPincode());
        body.add("consignee[state]", addr.getState());
        body.add("consignee[country_code]", "IN");

        // 2. Parcel Details
        body.add("parcel[type]", "P"); // P = Prepaid (online payment); D = COD
        body.add("parcel[value]", String.valueOf(order.getTotalAmount()));
        body.add("parcel[contents]", contents);
        
        // Root level physical attributes
        body.add("weight", String.valueOf(weight));
        body.add("weight_unit", "gm");
        body.add("length", String.valueOf(length));
        body.add("breadth", String.valueOf(breadth));
        body.add("height", String.valueOf(height));
        
        // Determine pickup address ID
        String pickupAddressId = customPickupAddressId;
        if (pickupAddressId == null || pickupAddressId.isEmpty()) {
            try {
                if (redisService != null) {
                    pickupAddressId = redisService.get("icarry_pickup_address_id");
                }
            } catch (Exception e) {
                log.warn("Failed to retrieve pickup address ID from Redis: {}", e.getMessage());
            }
        }
        if (pickupAddressId == null || pickupAddressId.isEmpty()) {
            pickupAddressId = config.getPickupAddressId();
        }
        body.add("pickup_address_id", pickupAddressId);

        try {
            // DO NOT RETRY shipment booking to prevent duplicates
            String responseBody = client.post("/api_add_shipment_surface", body, false);
            JsonNode root = objectMapper.readTree(responseBody);

            if (root.has("error")) {
                throw new IcarryApiException("Shipment booking failed: " + root.get("error").asText(), 400);
            }

            String shipmentId = root.has("shipment_id") ? root.get("shipment_id").asText() : null;
            if (shipmentId == null && root.has("id")) {
                shipmentId = root.get("id").asText();
            }

            if (shipmentId == null) {
                throw new IcarryApiException("Shipment booking response missing shipment_id: " + responseBody, 400);
            }

            String awb = root.has("awb") ? root.get("awb").asText() : 
                         (root.has("awb_number") ? root.get("awb_number").asText() : 
                          (root.has("tracking_number") ? root.get("tracking_number").asText() : ""));
            
            String courier = root.has("courier_name") ? root.get("courier_name").asText() : 
                             (root.has("courier") ? root.get("courier").asText() : "Standard Courier");

            double rate = root.has("charge") ? root.get("charge").asDouble() : 
                          (root.has("rate") ? root.get("rate").asDouble() : 0.0);

            // Save details to order entity
            order.setShipmentId(shipmentId);
            order.setAwbNumber(awb);
            order.setTrackingNumber(awb);
            order.setCourierName(courier);
            order.setShippingCharge(BigDecimal.valueOf(rate));
            order.setShipmentStatus("BOOKED");
            
            // Default 3-5 days delivery date
            order.setEstimatedDeliveryDate(LocalDateTime.now().plusDays(4));
            order.setLastTrackingSync(LocalDateTime.now());

            log.info("Shipment successfully booked for order ID: {}. Shipment ID: {}, AWB: {}", 
                     order.getId(), shipmentId, awb);

            return orderRepository.save(order);
        } catch (IcarryApiException e) {
            log.error("Failed to book shipment for order {}: {}", order.getId(), e.getMessage());
            throw e; // Re-throw with original status code preserved
        } catch (Exception e) {
            log.error("Unexpected error booking shipment for order {}: {}", order.getId(), e.getMessage());
            throw new IcarryApiException("Booking failed: " + e.getMessage(), 500, e);
        }
    }

    @Transactional
    public Order cancelShipment(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with ID: " + orderId));

        if (order.getShipmentId() == null) {
            throw new IcarryApiException("No shipment is booked for this order.", 400);
        }

        if ("CANCELLED".equalsIgnoreCase(order.getShipmentStatus())) {
            throw new ShipmentCancelledException("Shipment is already cancelled.");
        }

        if ("DELIVERED".equalsIgnoreCase(order.getShipmentStatus())) {
            throw new IcarryApiException("Cannot cancel a delivered shipment.", 400);
        }

        log.info("Cancelling shipment for order ID: {}, Shipment ID: {}", order.getId(), order.getShipmentId());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("shipment_id", order.getShipmentId());
        body.add("awb", order.getAwbNumber());

        try {
            // Cancel API is safe to retry
            String responseBody = client.post("/api_cancel_shipment", body, true);
            JsonNode root = objectMapper.readTree(responseBody);

            if (root.has("error")) {
                throw new IcarryApiException("Cancellation failed: " + root.get("error").asText(), 400);
            }

            // Clear shipment details so it can be re-booked
            order.setShipmentId(null);
            order.setAwbNumber(null);
            order.setCourierName(null);
            order.setTrackingNumber(null);
            order.setLabelUrl(null);
            order.setShipmentStatus("PENDING_BOOKING");
            order.setLastTrackingSync(LocalDateTime.now());

            log.info("Shipment cancelled successfully for order ID: {}", orderId);
            return orderRepository.save(order);
        } catch (IcarryApiException e) {
            log.error("Failed to cancel shipment for order {}: {}", orderId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error cancelling shipment for order {}: {}", orderId, e.getMessage());
            throw new IcarryApiException("Cancellation API failed: " + e.getMessage(), 500, e);
        }
    }

    private String cleanMobileNumber(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("\\D+", "");
        if (digits.length() > 10) {
            return digits.substring(digits.length() - 10);
        }
        return digits;
    }
}
