package com.lemonacademy.ecommerce.shipping.controller;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemonacademy.ecommerce.dto.OrderResponse;
import com.lemonacademy.ecommerce.entity.Order;
import com.lemonacademy.ecommerce.entity.Role;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.repository.OrderRepository;
import com.lemonacademy.ecommerce.shipping.dto.*;
import com.lemonacademy.ecommerce.shipping.service.*;
import com.lemonacademy.ecommerce.shipping.webhook.IcarryWebhookController;
import com.lemonacademy.ecommerce.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
public class ShippingControllersTest {

    private MockMvc mockMvcAdmin;
    private MockMvc mockMvcCustomer;
    private MockMvc mockMvcWebhook;

    @Mock
    private IcarryShipmentService shipmentService;

    @Mock
    private IcarryEstimateService estimateService;

    @Mock
    private IcarryTrackingService trackingService;

    @Mock
    private IcarryLabelService labelService;

    @Mock
    private IcarryPickupService pickupService;

    @Mock
    private IcarryWebhookService webhookService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private AdminShippingController adminController;

    @InjectMocks
    private CustomerShippingController customerController;

    @InjectMocks
    private IcarryWebhookController webhookController;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private User adminUser;
    private User customerUser;
    private Order order;
    private OrderResponse orderResponse;

    @BeforeEach
    void setUp() {
        com.lemonacademy.ecommerce.exception.GlobalExceptionHandler advice = new com.lemonacademy.ecommerce.exception.GlobalExceptionHandler();
        mockMvcAdmin = MockMvcBuilders.standaloneSetup(adminController).setControllerAdvice(advice).build();
        mockMvcCustomer = MockMvcBuilders.standaloneSetup(customerController).setControllerAdvice(advice).build();
        mockMvcWebhook = MockMvcBuilders.standaloneSetup(webhookController).setControllerAdvice(advice).build();

        adminUser = User.builder().id(UUID.fromString("0bbc4ab8-e7c0-3e38-88c8-59fd4801d7b4")).email("admin@example.com").role(Role.ADMIN).build();
        customerUser = User.builder().id(UUID.fromString("f6b94ab3-a544-3f41-a168-f01ee2e33f09")).email("customer@example.com").role(Role.CUSTOMER).build();
        
        order = Order.builder()
                .id(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))
                .orderNumber("LH-20260724-ABCD")
                .shipmentId("7462228")
                .courierName("Amazon Shipping")
                .user(customerUser)
                .awbNumber("372307931715")
                .shipmentStatus("BOOKED")
                .build();

        orderResponse = OrderResponse.builder()
                .id("23db3d7a-683b-372b-8036-95da3ae5c542")
                .orderNumber("LH-20260724-ABCD")
                .shipmentStatus("BOOKED")
                .build();
    }

    @Test
    void testAdminBookShipment() throws Exception {
        BookShipmentRequest request = new BookShipmentRequest("23db3d7a-683b-372b-8036-95da3ae5c542");
        when(orderRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.of(order));
        when(shipmentService.bookShipmentForOrder(any(Order.class), any())).thenReturn(order);
        when(orderService.getOrderDetails("23db3d7a-683b-372b-8036-95da3ae5c542")).thenReturn(orderResponse);

        mockMvcAdmin.perform(post("/api/admin/shipping/book")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Shipment booked successfully"))
                .andExpect(jsonPath("$.data.id").value("23db3d7a-683b-372b-8036-95da3ae5c542"));
    }

    @Test
    void testAdminCancelShipment() throws Exception {
        CancelShipmentRequest request = new CancelShipmentRequest("23db3d7a-683b-372b-8036-95da3ae5c542");
        when(orderRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.of(order));
        when(shipmentService.cancelShipment(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(order);
        when(orderService.getOrderDetails("23db3d7a-683b-372b-8036-95da3ae5c542")).thenReturn(orderResponse);

        mockMvcAdmin.perform(post("/api/admin/shipping/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("23db3d7a-683b-372b-8036-95da3ae5c542"));
    }

    @Test
    void testAdminGetEstimate() throws Exception {
        ShippingEstimateRequest request = ShippingEstimateRequest.builder()
                .originPincode("110001")
                .destinationPincode("400001")
                .weight(500)
                .build();
        CourierEstimateResponse est = CourierEstimateResponse.builder()
                .courierName("Delhivery").rate(BigDecimal.valueOf(120.0)).eta("3 days").build();
        when(estimateService.getEstimate(any(ShippingEstimateRequest.class))).thenReturn(Collections.singletonList(est));

        mockMvcAdmin.perform(post("/api/admin/shipping/estimate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].courierName").value("Delhivery"));
    }

    @Test
    void testAdminTrackShipment() throws Exception {
        TrackingResponse tracking = TrackingResponse.builder().status("IN_TRANSIT").build();
        when(trackingService.trackShipment("AWB123")).thenReturn(tracking);

        mockMvcAdmin.perform(get("/api/admin/shipping/track/AWB123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("IN_TRANSIT"));
    }

    @Test
    void testAdminGenerateLabelPdfStream() throws Exception {
        byte[] fakePdf = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};
        when(orderRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.of(order));
        when(labelService.getLabelPdfBytes(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(fakePdf);

        mockMvcAdmin.perform(get("/api/admin/shipping/label/23db3d7a-683b-372b-8036-95da3ae5c542")
                .accept(MediaType.APPLICATION_PDF))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"shipping-label-LH-20260724-ABCD.pdf\""))
                .andExpect(content().bytes(fakePdf));
    }

    @Test
    void testAdminGenerateLabelJsonExplicit() throws Exception {
        when(orderRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.of(order));
        when(labelService.generateLabel(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn("https://icarry.in/labels/7462228.pdf");

        mockMvcAdmin.perform(get("/api/admin/shipping/label/23db3d7a-683b-372b-8036-95da3ae5c542?format=json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("https://icarry.in/labels/7462228.pdf"));
    }

    @Test
    void testAdminDownloadLabelPdf() throws Exception {
        byte[] fakePdf = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};
        when(orderRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.of(order));
        when(labelService.getLabelPdfBytes(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(fakePdf);

        mockMvcAdmin.perform(get("/api/admin/shipping/label/23db3d7a-683b-372b-8036-95da3ae5c542/download"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"shipping-label-LH-20260724-ABCD.pdf\""))
                .andExpect(content().bytes(fakePdf));
    }

    @Test
    void testAdminGetLabelMetadata() throws Exception {
        LabelResponseDto metadata = LabelResponseDto.builder()
                .orderId(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))
                .orderNumber("LH-20260724-ABCD")
                .shipmentId("7462228")
                .awbNumber("372307931715")
                .courierName("Amazon Shipping")
                .labelUrl("https://icarry.in/labels/7462228.pdf")
                .downloadUrl("/api/admin/shipping/label/23db3d7a-683b-372b-8036-95da3ae5c542/download")
                .viewUrl("/api/admin/shipping/label/23db3d7a-683b-372b-8036-95da3ae5c542/pdf")
                .shipmentStatus("BOOKED")
                .build();

        when(orderRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.of(order));
        when(labelService.getLabelMetadata(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(metadata);

        mockMvcAdmin.perform(get("/api/admin/shipping/label/23db3d7a-683b-372b-8036-95da3ae5c542/url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shipmentId").value("7462228"))
                .andExpect(jsonPath("$.data.awbNumber").value("372307931715"))
                .andExpect(jsonPath("$.data.courierName").value("Amazon Shipping"))
                .andExpect(jsonPath("$.data.labelUrl").value("https://icarry.in/labels/7462228.pdf"));
    }

    @Test
    void testAdminPickupAddress() throws Exception {
        PickupAddressRequest req = PickupAddressRequest.builder()
                .contactPerson("Warehouse Mgr")
                .phone("9876543210")
                .addressLine1("Sector 4")
                .city("Noida")
                .state("UP")
                .pincode("201301")
                .country("IN")
                .build();
        when(pickupService.createOrUpdatePickupAddress(any())).thenReturn("ADDR_100");

        mockMvcAdmin.perform(post("/api/admin/shipping/pickup/address")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("ADDR_100"));
    }

    @Test
    void testAdminPickupRequest() throws Exception {
        when(orderRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.of(order));
        when(pickupService.requestPickup(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(order);
        when(orderService.getOrderDetails("23db3d7a-683b-372b-8036-95da3ae5c542")).thenReturn(orderResponse);

        mockMvcAdmin.perform(post("/api/admin/shipping/pickup/request/23db3d7a-683b-372b-8036-95da3ae5c542"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("23db3d7a-683b-372b-8036-95da3ae5c542"));
    }

    @Test
    void testCustomerTrackOwnShipmentSuccess() throws Exception {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                customerUser, null, customerUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(orderRepository.findByAwbNumber("AWB123")).thenReturn(Optional.of(order));
        TrackingResponse tracking = TrackingResponse.builder().status("DELIVERED").build();
        when(trackingService.trackShipment("AWB123")).thenReturn(tracking);

        mockMvcCustomer.perform(get("/api/shipping/track/AWB123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DELIVERED"));

        SecurityContextHolder.clearContext();
    }

    @Test
    void testCustomerTrackOwnShipmentUnauthorized() throws Exception {
        User otherCustomer = User.builder().id(UUID.fromString("08d0a55d-b72b-3fb8-ad6b-1d041bd7e52b")).email("other@example.com").role(Role.CUSTOMER).build();
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                otherCustomer, null, otherCustomer.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(orderRepository.findByAwbNumber("AWB123")).thenReturn(Optional.of(order));

        mockMvcCustomer.perform(get("/api/shipping/track/AWB123"))
                .andExpect(status().is4xxClientError());

        SecurityContextHolder.clearContext();
    }

    @Test
    void testWebhookCallback() throws Exception {
        IcarryWebhookPayload payload = IcarryWebhookPayload.builder()
                .awb("AWB123")
                .status(21)
                .token("API_KEY_123")
                .build();
        doNothing().when(webhookService).processWebhook(any(IcarryWebhookPayload.class));

        mockMvcWebhook.perform(post("/api/webhooks/icarry")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }
}
