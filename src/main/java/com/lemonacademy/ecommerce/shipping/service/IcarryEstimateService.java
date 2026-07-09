package com.lemonacademy.ecommerce.shipping.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemonacademy.ecommerce.shipping.client.IcarryClient;
import com.lemonacademy.ecommerce.shipping.config.IcarryConfig;
import com.lemonacademy.ecommerce.shipping.dto.CourierEstimateResponse;
import com.lemonacademy.ecommerce.shipping.dto.ShippingEstimateRequest;
import com.lemonacademy.ecommerce.shipping.util.DimensionParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class IcarryEstimateService {

    private final IcarryClient client;
    private final IcarryConfig config;
    private final ObjectMapper objectMapper;

    public IcarryEstimateService(IcarryClient client, IcarryConfig config, ObjectMapper objectMapper) {
        this.client = client;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    public List<CourierEstimateResponse> getEstimate(ShippingEstimateRequest request) {
        log.info("Requesting shipment estimate from: {} to: {} for weight: {}g", 
                 request.getOriginPincode(), request.getDestinationPincode(), request.getWeight());

        // Parse dimensions
        int length = request.getLength() != null ? request.getLength() : 10;
        int breadth = request.getBreadth() != null ? request.getBreadth() : 10;
        int height = request.getHeight() != null ? request.getHeight() : 10;
        
        if (request.getLength() == null || request.getBreadth() == null || request.getHeight() == null) {
            int[] dims = DimensionParser.parse(config.getDefaultDimensions(), 10);
            length = dims[0];
            breadth = dims[1];
            height = dims[2];
        }

        int weight = request.getWeight() != null ? request.getWeight() : config.getDefaultWeight();

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("length", String.valueOf(length));
        body.add("breadth", String.valueOf(breadth));
        body.add("height", String.valueOf(height));
        body.add("weight", String.valueOf(weight));
        body.add("origin_pincode", request.getOriginPincode());
        body.add("destination_pincode", request.getDestinationPincode());
        body.add("origin_country_code", request.getOriginCountryCode() != null ? request.getOriginCountryCode() : "IN");
        body.add("destination_country_code", request.getDestinationCountryCode() != null ? request.getDestinationCountryCode() : "IN");
        body.add("shipment_mode", request.getShipmentMode() != null ? request.getShipmentMode() : "E");
        body.add("parcel_type", request.getParcelType() != null ? request.getParcelType() : "P");
        body.add("parcel_value", request.getParcelValue() != null ? request.getParcelValue().toString() : "100.00");

        try {
            String responseBody = client.post("/api_get_estimate", body, true);
            JsonNode root = objectMapper.readTree(responseBody);
            
            if (root.isObject() && root.has("error")) {
                String errorMsg = root.get("error").toString();
                log.error("iCarry Estimate API returned error: {}", errorMsg);
                throw new com.lemonacademy.ecommerce.shipping.exception.IcarryApiException("iCarry Estimate API Error: " + errorMsg, 400);
            }
            
            List<CourierEstimateResponse> list = new ArrayList<>();
            
            // Map rates - support different potential response structures from iCarry API
            if (root.isArray()) {
                for (JsonNode node : root) {
                    list.add(mapToResponse(node));
                }
            } else if (root.has("couriers")) {
                JsonNode couriers = root.get("couriers");
                if (couriers.isArray()) {
                    for (JsonNode node : couriers) {
                        list.add(mapToResponse(node));
                    }
                }
            } else if (root.has("rates")) {
                JsonNode rates = root.get("rates");
                if (rates.isArray()) {
                    for (JsonNode node : rates) {
                        list.add(mapToResponse(node));
                    }
                }
            } else {
                // If it's a flat object containing individual courier rates
                root.fields().forEachRemaining(entry -> {
                    JsonNode val = entry.getValue();
                    if (val.isObject() && val.has("rate")) {
                        list.add(mapToResponse(val));
                    }
                });
            }
            
            return list;
        } catch (Exception e) {
            log.error("Failed to parse estimate response: {}", e.getMessage());
            throw new RuntimeException("Error fetching shipping estimates: " + e.getMessage(), e);
        }
    }

    private CourierEstimateResponse mapToResponse(JsonNode node) {
        String courier = node.has("courier_name") ? node.get("courier_name").asText() : 
                        (node.has("courier") ? node.get("courier").asText() : "Standard Courier");
        double rate = node.has("rate") ? node.get("rate").asDouble() : 
                     (node.has("charge") ? node.get("charge").asDouble() : 0.0);
        String eta = node.has("eta") ? node.get("eta").asText() : 
                    (node.has("delivery_days") ? node.get("delivery_days").asText() : "3-5 days");
        
        return CourierEstimateResponse.builder()
                .courierName(courier)
                .rate(BigDecimal.valueOf(rate))
                .eta(eta)
                .build();
    }
}
