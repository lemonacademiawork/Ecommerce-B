package com.lemonacademy.ecommerce.shipping.service;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemonacademy.ecommerce.entity.Address;
import com.lemonacademy.ecommerce.entity.Order;
import com.lemonacademy.ecommerce.entity.OrderItem;
import com.lemonacademy.ecommerce.entity.Product;
import com.lemonacademy.ecommerce.repository.OrderRepository;
import com.lemonacademy.ecommerce.shipping.client.IcarryClient;
import com.lemonacademy.ecommerce.shipping.config.IcarryConfig;
import com.lemonacademy.ecommerce.shipping.dto.*;
import com.lemonacademy.ecommerce.shipping.exception.DuplicateShipmentException;
import com.lemonacademy.ecommerce.shipping.exception.IcarryApiException;
import com.lemonacademy.ecommerce.shipping.exception.ShipmentCancelledException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ShippingServicesTest {

    @Mock
    private IcarryClient client;

    @Mock
    private IcarryConfig config;

    @Mock
    private OrderRepository orderRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private IcarryEstimateService estimateService;
    private IcarryShipmentService shipmentService;
    private IcarryTrackingService trackingService;
    private IcarryWebhookService webhookService;
    private IcarryLabelService labelService;
    private IcarryPickupService pickupService;

    private Order order;

    @BeforeEach
    void setUp() {
        estimateService = new IcarryEstimateService(client, config, objectMapper);
        shipmentService = new IcarryShipmentService(client, config, orderRepository, objectMapper, null);
        trackingService = new IcarryTrackingService(client, orderRepository, objectMapper);
        webhookService = new IcarryWebhookService(orderRepository, config);
        ShippingLabelPdfGenerator pdfGenerator = new ShippingLabelPdfGenerator();
        labelService = new IcarryLabelService(client, config, orderRepository, objectMapper, pdfGenerator);
        pickupService = new IcarryPickupService(client, orderRepository, objectMapper, null);

        Address address = Address.builder()
                .fullName("John Doe")
                .phone("9876543210")
                .addressLine1("123 Street")
                .city("New Delhi")
                .state("DL")
                .pincode("110001")
                .build();

        Product product = Product.builder()
                .name("Item A")
                .price(BigDecimal.valueOf(100.0))
                .build();

        OrderItem item = OrderItem.builder()
                .product(product)
                .quantity(2)
                .build();

        order = Order.builder()
                .id(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))
                .orderNumber("LH-20260724-ABCD")
                .address(address)
                .totalAmount(BigDecimal.valueOf(200.0))
                .items(List.of(item))
                .build();
    }

    @Test
    void testGetEstimate() {
        String responseJson = "{\"couriers\":[{\"courier_name\":\"Delhivery\",\"rate\":120.00,\"eta\":\"3-5 days\"}]}";
        when(client.post(eq("/api_get_estimate"), any(), eq(true))).thenReturn(responseJson);
        when(config.getDefaultDimensions()).thenReturn("10x10x10");

        ShippingEstimateRequest request = ShippingEstimateRequest.builder()
                .originPincode("110001")
                .destinationPincode("400001")
                .weight(500)
                .build();

        List<CourierEstimateResponse> estimates = estimateService.getEstimate(request);

        assertNotNull(estimates);
        assertEquals(1, estimates.size());
        assertEquals("Delhivery", estimates.get(0).getCourierName());
        assertEquals(BigDecimal.valueOf(120.0), estimates.get(0).getRate());
    }

    @Test
    void testBookShipmentSuccess() {
        String responseJson = "{\"shipment_id\":\"SHIP123\",\"awb\":\"AWB123\",\"courier_name\":\"FedEx\",\"charge\":150.00}";
        when(client.post(eq("/api_add_shipment_surface"), any(), eq(false))).thenReturn(responseJson);
        when(config.getDefaultDimensions()).thenReturn("10x10x10");
        when(config.getDefaultWeight()).thenReturn(500);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order updatedOrder = shipmentService.bookShipmentForOrder(order);

        assertNotNull(updatedOrder);
        assertEquals("SHIP123", updatedOrder.getShipmentId());
        assertEquals("AWB123", updatedOrder.getAwbNumber());
        assertEquals("FedEx", updatedOrder.getCourierName());
        assertEquals(BigDecimal.valueOf(150.0), updatedOrder.getShippingCharge());
        assertEquals("BOOKED", updatedOrder.getShipmentStatus());
    }

    @Test
    void testBookShipmentDuplicate() {
        order.setShipmentId("SHIP_EXISTING");

        assertThrows(DuplicateShipmentException.class, () -> {
            shipmentService.bookShipmentForOrder(order);
        });
    }

    @Test
    void testCancelShipmentSuccess() {
        order.setShipmentId("SHIP123");
        order.setAwbNumber("AWB123");
        order.setShipmentStatus("BOOKED");

        String responseJson = "{\"success\":true}";
        when(orderRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.of(order));
        when(client.post(eq("/api_cancel_shipment"), any(), eq(true))).thenReturn(responseJson);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order cancelledOrder = shipmentService.cancelShipment(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));
 
        assertEquals("PENDING_BOOKING", cancelledOrder.getShipmentStatus());
        assertNull(cancelledOrder.getStatus());
        assertNull(cancelledOrder.getAwbNumber());
        assertNull(cancelledOrder.getShipmentId());
    }

    @Test
    void testCancelShipmentDuplicate() {
        order.setShipmentId("SHIP123");
        order.setShipmentStatus("CANCELLED");
        when(orderRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.of(order));

        assertThrows(ShipmentCancelledException.class, () -> {
            shipmentService.cancelShipment(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));
        });
    }

    @Test
    void testTrackShipment() {
        String responseJson = "{\"status\":\"IN_TRANSIT\",\"courier\":\"FedEx\",\"shipment_id\":\"SHIP123\",\"history\":[{\"time\":\"2026-07-09 10:00\",\"location\":\"Delhi\",\"activity\":\"Picked up\"}]}";
        when(client.post(eq("/api_track_shipment"), any(), eq(true))).thenReturn(responseJson);

        TrackingResponse response = trackingService.trackShipment("AWB123");

        assertNotNull(response);
        assertEquals("IN_TRANSIT", response.getStatus());
        assertEquals("FedEx", response.getCourierName());
        assertEquals(1, response.getEvents().size());
        assertEquals("Picked up", response.getEvents().get(0).getActivity());
    }

    @Test
    void testWebhookProcessing() {
        order.setAwbNumber("AWB123");
        order.setShipmentStatus("BOOKED");

        when(orderRepository.findByAwbNumber("AWB123")).thenReturn(Optional.of(order));
        when(config.getApiKey()).thenReturn("MOCK_KEY");
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IcarryWebhookPayload payload = IcarryWebhookPayload.builder()
                .awb("AWB123")
                .status(21) // 21 corresponds to Delivered in mapping
                .token("MOCK_KEY")
                .build();

        webhookService.processWebhook(payload);

        assertEquals("DELIVERED", order.getShipmentStatus());
        assertEquals(com.lemonacademy.ecommerce.entity.OrderStatus.DELIVERED, order.getStatus());
    }

    @Test
    void testGenerateLabel() {
        order.setShipmentId("7462228");
        order.setAwbNumber("372307931715");

        String responseJson = "{\"label_url\":\"https://icarry.in/labels/7462228.pdf\"}";
        when(orderRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.of(order));
        when(client.post(eq("/api_print_label"), any(), eq(true))).thenReturn(responseJson);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String labelUrl = labelService.generateLabel(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));

        assertEquals("https://icarry.in/labels/7462228.pdf", labelUrl);
        assertEquals("https://icarry.in/labels/7462228.pdf", order.getLabelUrl());
    }

    @Test
    void testGetLabelPdfBytes() {
        order.setShipmentId("7462228");
        order.setAwbNumber("372307931715");
        order.setLabelUrl("https://icarry.in/labels/7462228.pdf");

        byte[] fakePdf = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34}; // %PDF-1.4
        when(orderRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.of(order));
        when(client.downloadBinary(eq("https://icarry.in/labels/7462228.pdf"))).thenReturn(fakePdf);

        byte[] result = labelService.getLabelPdfBytes(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));

        assertNotNull(result);
        assertArrayEquals(fakePdf, result);
    }

    @Test
    void testGetLabelPdfBytesInternalFallback() {
        order.setShipmentId("7462228");
        order.setAwbNumber("372307931715");
        order.setCourierName("Amazon Shipping");
        order.setLabelUrl(null);

        when(orderRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.of(order));

        byte[] result = labelService.getLabelPdfBytes(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));

        assertNotNull(result);
        assertTrue(result.length > 100);
        // Verify PDF Magic Bytes (%PDF)
        assertEquals(0x25, result[0]);
        assertEquals(0x50, result[1]);
        assertEquals(0x44, result[2]);
        assertEquals(0x46, result[3]);
    }

    @Test
    void testGetLabelMetadata() {
        order.setShipmentId("7462228");
        order.setAwbNumber("372307931715");
        order.setCourierName("Amazon Shipping");
        order.setLabelUrl("https://icarry.in/labels/7462228.pdf");
        order.setShipmentStatus("BOOKED");

        when(orderRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.of(order));

        LabelResponseDto metadata = labelService.getLabelMetadata(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));

        assertNotNull(metadata);
        assertEquals("7462228", metadata.getShipmentId());
        assertEquals("372307931715", metadata.getAwbNumber());
        assertEquals("Amazon Shipping", metadata.getCourierName());
        assertEquals("https://icarry.in/labels/7462228.pdf", metadata.getLabelUrl());
        assertTrue(metadata.getDownloadUrl().contains("download"));
        assertTrue(metadata.getViewUrl().contains("pdf"));
    }

    @Test
    void testPickupScheduling() {
        order.setShipmentId("SHIP123");
        order.setAwbNumber("AWB123");
        order.setPickupRequested(false);

        String responseJson = "{\"success\":true}";
        when(orderRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.of(order));
        when(client.post(eq("/api_request_pickup"), any(), eq(true))).thenReturn(responseJson);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order updatedOrder = pickupService.requestPickup(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));

        assertTrue(updatedOrder.getPickupRequested());
        assertNotNull(updatedOrder.getPickupDate());
    }
}
