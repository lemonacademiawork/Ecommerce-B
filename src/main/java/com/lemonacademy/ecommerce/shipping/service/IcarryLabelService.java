package com.lemonacademy.ecommerce.shipping.service;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemonacademy.ecommerce.entity.Order;
import com.lemonacademy.ecommerce.repository.OrderRepository;
import com.lemonacademy.ecommerce.shipping.client.IcarryClient;
import com.lemonacademy.ecommerce.shipping.config.IcarryConfig;
import com.lemonacademy.ecommerce.shipping.dto.LabelResponseDto;
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
    private final IcarryConfig config;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;
    private final ShippingLabelPdfGenerator pdfGenerator;

    public IcarryLabelService(IcarryClient client, IcarryConfig config, 
                               OrderRepository orderRepository, ObjectMapper objectMapper,
                               ShippingLabelPdfGenerator pdfGenerator) {
        this.client = client;
        this.config = config;
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
        this.pdfGenerator = pdfGenerator;
    }

    @Transactional
    public String generateLabel(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with ID: " + orderId));

        if (order.getShipmentId() == null || order.getShipmentId().trim().isEmpty()) {
            throw new IcarryApiException("No shipment booked for order ID: " + orderId + ". Please book a shipment first.", 400);
        }

        if (order.getLabelUrl() != null && !order.getLabelUrl().trim().isEmpty()) {
            log.info("Using existing shipping label URL for order ID: {}, Shipment ID: {}", order.getId(), order.getShipmentId());
            return order.getLabelUrl();
        }

        log.info("Requesting shipping label generation for order ID: {}, Shipment ID: {}, AWB: {}", 
                 order.getId(), order.getShipmentId(), order.getAwbNumber());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("shipment_id", order.getShipmentId());
        if (order.getAwbNumber() != null && !order.getAwbNumber().trim().isEmpty()) {
            body.add("awb", order.getAwbNumber());
        }

        try {
            String responseBody = null;
            try {
                responseBody = client.post("/api_print_label", body, true);
            } catch (IcarryApiException ex) {
                log.warn("POST /api_print_label returned {}: {}. Attempting fallback to /api_label", 
                         ex.getStatusCode(), ex.getMessage());
                try {
                    responseBody = client.post("/api_label", body, true);
                } catch (IcarryApiException ex2) {
                    log.warn("POST /api_label also returned {}: {}. Will use internal high-res shipping label generator.", 
                             ex2.getStatusCode(), ex2.getMessage());
                }
            }

            String url = extractLabelUrl(responseBody, order.getShipmentId());
            if (url != null && !url.trim().isEmpty()) {
                url = normalizeUrl(url);
                order.setLabelUrl(url);
                orderRepository.save(order);
                log.info("Successfully received remote carrier shipping label URL: {}", url);
                return url;
            }
        } catch (Exception e) {
            log.warn("Carrier label endpoint call failed: {}. Falling back to internal PDF generation endpoint.", e.getMessage());
        }

        // Fallback to internal direct label stream URL
        String internalUrl = "/api/admin/shipping/label/" + order.getId() + "/pdf";
        order.setLabelUrl(internalUrl);
        orderRepository.save(order);
        log.info("Using internal shipping label PDF endpoint for order ID: {}: {}", orderId, internalUrl);
        return internalUrl;
    }

    @Transactional
    public byte[] getLabelPdfBytes(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with ID: " + orderId));

        if (order.getShipmentId() == null || order.getShipmentId().trim().isEmpty()) {
            throw new IcarryApiException("No shipment booked for order ID: " + orderId + ". Please book a shipment first.", 400);
        }

        String labelUrl = order.getLabelUrl();

        // 1. If we have an external remote carrier URL, attempt to download
        if (labelUrl != null && !labelUrl.trim().isEmpty() && (labelUrl.startsWith("http://") || labelUrl.startsWith("https://"))) {
            try {
                log.info("Downloading shipping label PDF from remote URL for order ID: {}, Shipment ID: {}", 
                         order.getId(), order.getShipmentId());
                byte[] bytes = client.downloadBinary(labelUrl);
                if (isValidPdf(bytes)) {
                    return bytes;
                }
                log.warn("Remote binary from {} did not appear to be a valid PDF. Generating internally.", labelUrl);
            } catch (Exception e) {
                log.warn("Failed to download PDF from remote label URL: {}. Generating internally.", e.getMessage());
            }
        }

        // 2. Generate standard carrier-compliant PDF with barcode dynamically
        log.info("Generating standard dynamic shipping label PDF for order ID: {}, AWB: {}, Courier: {}", 
                 orderId, order.getAwbNumber(), order.getCourierName());
        return pdfGenerator.generateShippingLabel(order);
    }

    @Transactional(readOnly = true)
    public LabelResponseDto getLabelMetadata(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with ID: " + orderId));

        String orderIdStr = order.getId().toString();
        String labelUrl = order.getLabelUrl();
        if (labelUrl == null || labelUrl.trim().isEmpty()) {
            labelUrl = "/api/admin/shipping/label/" + orderIdStr + "/pdf";
        }

        return LabelResponseDto.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .shipmentId(order.getShipmentId())
                .awbNumber(order.getAwbNumber())
                .courierName(order.getCourierName())
                .labelUrl(labelUrl)
                .downloadUrl("/api/admin/shipping/label/" + orderIdStr + "/download")
                .viewUrl("/api/admin/shipping/label/" + orderIdStr + "/pdf")
                .shipmentStatus(order.getShipmentStatus())
                .build();
    }

    private String extractLabelUrl(String responseBody, String shipmentId) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);

            if (isErrorResponse(root)) {
                String errorMsg = getErrorMessage(root);
                log.warn("Shipping carrier label returned error message: {}", errorMsg);
                return null;
            }

            // Check standard URL fields in JSON
            String[] urlFields = new String[]{
                    "label_url", "url", "label", "pdf_url", "label_pdf", 
                    "download_url", "file", "print_url", "link", "document_url"
            };

            for (String field : urlFields) {
                if (root.has(field) && !root.get(field).isNull() && !root.get(field).asText().isBlank()) {
                    return root.get(field).asText().trim();
                }
            }

            // Check nested objects
            if (root.has("data")) {
                JsonNode dataNode = root.get("data");
                if (dataNode.isTextual() && dataNode.asText().startsWith("http")) {
                    return dataNode.asText().trim();
                }
                for (String field : urlFields) {
                    if (dataNode.has(field) && !dataNode.get(field).isNull() && !dataNode.get(field).asText().isBlank()) {
                        return dataNode.get(field).asText().trim();
                    }
                }
            }

            if (root.has("labels") && root.get("labels").isArray() && root.get("labels").size() > 0) {
                JsonNode firstLabel = root.get("labels").get(0);
                if (firstLabel.isTextual()) {
                    return firstLabel.asText().trim();
                }
                for (String field : urlFields) {
                    if (firstLabel.has(field) && !firstLabel.get(field).isNull() && !firstLabel.get(field).asText().isBlank()) {
                        return firstLabel.get(field).asText().trim();
                    }
                }
            }

        } catch (Exception e) {
            String trimmed = responseBody.trim();
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                return trimmed;
            }
        }

        return null;
    }

    private boolean isErrorResponse(JsonNode root) {
        if (root.has("error")) {
            JsonNode err = root.get("error");
            if (err.isBoolean()) return err.asBoolean();
            if (err.isTextual()) return !err.asText().isBlank();
            return true;
        }
        if (root.has("status")) {
            String status = root.get("status").asText();
            if ("error".equalsIgnoreCase(status) || "failed".equalsIgnoreCase(status) || "false".equalsIgnoreCase(status)) {
                return true;
            }
        }
        if (root.has("success")) {
            JsonNode succ = root.get("success");
            if (succ.isBoolean()) return !succ.asBoolean();
            if (succ.isNumber()) return succ.asInt() == 0;
            if (succ.isTextual()) return "false".equalsIgnoreCase(succ.asText()) || "0".equals(succ.asText());
        }
        return false;
    }

    private String getErrorMessage(JsonNode root) {
        if (root.has("error")) {
            return root.get("error").asText();
        }
        if (root.has("message")) {
            return root.get("message").asText();
        }
        return root.toString();
    }

    private String normalizeUrl(String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        String base = config.getBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        return base + trimmed;
    }

    private boolean isValidPdf(byte[] bytes) {
        if (bytes == null || bytes.length < 4) return false;
        // Check for PDF header (%PDF)
        return bytes[0] == 0x25 && bytes[1] == 0x50 && bytes[2] == 0x44 && bytes[3] == 0x46;
    }
}
