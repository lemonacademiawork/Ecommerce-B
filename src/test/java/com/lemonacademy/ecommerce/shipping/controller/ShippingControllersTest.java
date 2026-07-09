package com.lemonacademy.ecommerce.shipping.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemonacademy.ecommerce.entity.Order;
import com.lemonacademy.ecommerce.entity.Role;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.repository.OrderRepository;
import com.lemonacademy.ecommerce.shipping.dto.*;
import com.lemonacademy.ecommerce.shipping.service.*;
import com.lemonacademy.ecommerce.shipping.webhook.IcarryWebhookController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @BeforeEach
    void setUp() {
        com.lemonacademy.ecommerce.exception.GlobalExceptionHandler advice = new com.lemonacademy.ecommerce.exception.GlobalExceptionHandler();
        mockMvcAdmin = MockMvcBuilders.standaloneSetup(adminController).setControllerAdvice(advice).build();
        mockMvcCustomer = MockMvcBuilders.standaloneSetup(customerController).setControllerAdvice(advice).build();
        mockMvcWebhook = MockMvcBuilders.standaloneSetup(webhookController).setControllerAdvice(advice).build();

        adminUser = User.builder().id(100L).email("admin@example.com").role(Role.ADMIN).build();
        customerUser = User.builder().id(200L).email("customer@example.com").role(Role.CUSTOMER).build();
        
        order = Order.builder()
                .id(1L)
                .user(customerUser)
                .awbNumber("AWB123")
                .shipmentStatus("BOOKED")
                .build();
    }

    @Test
    void testAdminBookShipment() throws Exception {
        BookShipmentRequest request = new BookShipmentRequest(1L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(shipmentService.bookShipmentForOrder(any(Order.class))).thenReturn(order);

        mockMvcAdmin.perform(post("/api/admin/shipping/book")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Shipment booked successfully"));
    }

    @Test
    void testAdminCancelShipment() throws Exception {
        CancelShipmentRequest request = new CancelShipmentRequest(1L);
        when(shipmentService.cancelShipment(1L)).thenReturn(order);

        mockMvcAdmin.perform(post("/api/admin/shipping/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
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
    void testAdminGenerateLabel() throws Exception {
        when(labelService.generateLabel(1L)).thenReturn("https://icarry.in/labels/1.pdf");

        mockMvcAdmin.perform(get("/api/admin/shipping/label/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("https://icarry.in/labels/1.pdf"));
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
        when(pickupService.requestPickup(1L)).thenReturn(order);

        mockMvcAdmin.perform(post("/api/admin/shipping/pickup/request/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
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
        User otherCustomer = User.builder().id(999L).email("other@example.com").role(Role.CUSTOMER).build();
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                otherCustomer, null, otherCustomer.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(orderRepository.findByAwbNumber("AWB123")).thenReturn(Optional.of(order));

        mockMvcCustomer.perform(get("/api/shipping/track/AWB123"))
                .andExpect(status().is4xxClientError()); // Triggers global handler which maps UnauthorizedAccessException to 403 Forbidden or similar.

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
