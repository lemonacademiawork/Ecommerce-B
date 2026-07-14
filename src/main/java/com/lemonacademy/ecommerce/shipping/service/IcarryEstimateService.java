package com.lemonacademy.ecommerce.shipping.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemonacademy.ecommerce.shipping.client.IcarryClient;
import com.lemonacademy.ecommerce.shipping.config.IcarryConfig;
import com.lemonacademy.ecommerce.shipping.dto.CourierEstimateResponse;
import com.lemonacademy.ecommerce.shipping.dto.ShippingEstimateRequest;
import com.lemonacademy.ecommerce.shipping.exception.IcarryApiException;
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
        body.add("from_pincode", request.getOriginPincode());
        body.add("to_pincode", request.getDestinationPincode());
        log.info("Constructed iCarry estimate payload: {}", body);
        body.add("origin_country_code", request.getOriginCountryCode() != null ? request.getOriginCountryCode() : "IN");
        body.add("destination_country_code", request.getDestinationCountryCode() != null ? request.getDestinationCountryCode() : "IN");
        body.add("shipment_mode", request.getShipmentMode() != null ? request.getShipmentMode() : "E");
        body.add("shipment_type", request.getParcelType() != null ? request.getParcelType() : "P");
        body.add("shipment_value", request.getParcelValue() != null && request.getParcelValue().compareTo(java.math.BigDecimal.ZERO) > 0 ? request.getParcelValue().toString() : "100.00");

        try {
            String responseBody = client.post("/api_get_estimate", body, true);
            JsonNode root = objectMapper.readTree(responseBody);
            
            if (root.isObject() && root.has("error")) {
                String errorMsg = root.get("error").asText();
                if (errorMsg != null && !errorMsg.isBlank()) {
                    log.error("iCarry Estimate API returned error: {}", errorMsg);
                    throw new IcarryApiException("iCarry Estimate API Error: " + errorMsg, 400);
                }
            }
            
            List<CourierEstimateResponse> list = new ArrayList<>();
            
            // Map rates - iCarry returns { "success": 1, "estimate": [ ... ] }
            if (root.has("estimate") && root.get("estimate").isArray()) {
                for (JsonNode node : root.get("estimate")) {
                    list.add(mapToResponse(node));
                }
            } else if (root.isArray()) {
                for (JsonNode node : root) {
                    list.add(mapToResponse(node));
                }
            } else if (root.has("couriers") && root.get("couriers").isArray()) {
                for (JsonNode node : root.get("couriers")) {
                    list.add(mapToResponse(node));
                }
            }
            
            return list;
        } catch (IcarryApiException e) {
            log.error("iCarry estimate API error: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse estimate response: {}", e.getMessage());
            throw new IcarryApiException(
                    "Error fetching shipping estimates: " + e.getMessage(), 500, e);
        }
    }

    private CourierEstimateResponse mapToResponse(JsonNode node) {
        // iCarry returns: courier_id, courier_name, courier_group_name, courier_cost
        String courierId = node.has("courier_id") ? node.get("courier_id").asText() : null;
        String courierName = node.has("courier_name") ? node.get("courier_name").asText() : 
                            (node.has("courier") ? node.get("courier").asText() : "Standard Courier");
        String courierGroupName = node.has("courier_group_name") ? node.get("courier_group_name").asText() : null;
        
        double rate = node.has("courier_cost") ? node.get("courier_cost").asDouble() : 
                     (node.has("rate") ? node.get("rate").asDouble() : 
                     (node.has("charge") ? node.get("charge").asDouble() : 0.0));
        
        String eta = node.has("eta") ? node.get("eta").asText() : 
                    (node.has("delivery_days") ? node.get("delivery_days").asText() : "3-5 days");
        
        return CourierEstimateResponse.builder()
                .courierId(courierId)
                .courierName(courierName)
                .courierGroupName(courierGroupName)
                .rate(BigDecimal.valueOf(rate))
                .eta(eta)
                .build();
    }
}
