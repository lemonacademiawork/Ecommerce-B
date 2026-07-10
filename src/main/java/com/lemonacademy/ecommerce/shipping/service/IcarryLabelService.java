package com.lemonacademy.ecommerce.shipping.service;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemonacademy.ecommerce.entity.Order;
import com.lemonacademy.ecommerce.repository.OrderRepository;
import com.lemonacademy.ecommerce.shipping.client.IcarryClient;
import com.lemonacademy.ecommerce.shipping.exception.IcarryApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Service
@Slf4j
public class IcarryLabelService {

    private final IcarryClient client;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    public IcarryLabelService(IcarryClient client, OrderRepository orderRepository, ObjectMapper objectMapper) {
        this.client = client;
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public String generateLabel(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with ID: " + orderId));

        if (order.getShipmentId() == null || order.getShipmentId().isEmpty()) {
            throw new IcarryApiException("No shipment booked for order ID: " + orderId + ". Please book a shipment first.");
        }

        if (order.getLabelUrl() != null && !order.getLabelUrl().isEmpty()) {
            return order.getLabelUrl();
        }

        log.info("Generating shipping label for order ID: {}, Shipment ID: {}", order.getId(), order.getShipmentId());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("shipment_id", order.getShipmentId());
        body.add("awb", order.getAwbNumber());

        try {
            // Label generation is safe to retry
            String responseBody = client.post("/api_label", body, true);
            JsonNode root = objectMapper.readTree(responseBody);

            if (root.has("error")) {
                throw new IcarryApiException("Label generation failed: " + root.get("error").toString());
            }

            String url = root.has("label_url") ? root.get("label_url").asText() : 
                         (root.has("url") ? root.get("url").asText() : "");

            if (url == null || url.isEmpty()) {
                throw new IcarryApiException("Response from iCarry missing label_url: " + responseBody);
            }

            order.setLabelUrl(url);
            orderRepository.save(order);

            return url;
        } catch (IcarryApiException e) {
            log.error("Failed to generate label for order {}: {}", orderId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error generating label for order {}: {}", orderId, e.getMessage());
            throw new IcarryApiException("Label API failed: " + e.getMessage(), 500, e);
        }
    }
}
