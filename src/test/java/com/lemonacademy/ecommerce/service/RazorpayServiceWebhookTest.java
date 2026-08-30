package com.lemonacademy.ecommerce.service;

import com.lemonacademy.ecommerce.dto.RazorpayVerifyRequest;
import com.lemonacademy.ecommerce.entity.Order;
import com.lemonacademy.ecommerce.entity.OrderStatus;
import com.lemonacademy.ecommerce.entity.PaymentMethod;
import com.lemonacademy.ecommerce.entity.PaymentStatus;
import com.lemonacademy.ecommerce.exception.InvalidOperationException;
import com.lemonacademy.ecommerce.repository.OrderRepository;
import com.razorpay.RazorpayClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RazorpayServiceWebhookTest {

    @Mock
    private RazorpayClient razorpayClient;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private RazorpayService razorpayService;

    private final String webhookSecret = "test_webhook_secret_12345";
    private final String keySecret = "test_key_secret_67890";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(razorpayService, "webhookSecret", webhookSecret);
        ReflectionTestUtils.setField(razorpayService, "keySecret", keySecret);
    }

    private String calculateHmacSha256(String data, String secret) throws Exception {
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256Hmac.init(secretKey);
        byte[] hash = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    @Test
    void testProcessWebhook_PaymentCaptured_Success() throws Exception {
        String razorpayOrderId = "order_O123456789";
        String razorpayPaymentId = "pay_P987654321";
        String payload = "{\n" +
                "  \"event\": \"payment.captured\",\n" +
                "  \"payload\": {\n" +
                "    \"payment\": {\n" +
                "      \"entity\": {\n" +
                "        \"id\": \"" + razorpayPaymentId + "\",\n" +
                "        \"order_id\": \"" + razorpayOrderId + "\",\n" +
                "        \"amount\": 50000\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";

        String signature = calculateHmacSha256(payload, webhookSecret);

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .orderNumber("LH-20260826-0001")
                .razorpayOrderId(razorpayOrderId)
                .paymentStatus(PaymentStatus.PENDING)
                .status(OrderStatus.PENDING)
                .build();

        when(orderRepository.findByRazorpayOrderId(razorpayOrderId)).thenReturn(Optional.of(order));

        razorpayService.processWebhook(payload, signature);

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getPaymentMethod()).isEqualTo(PaymentMethod.RAZORPAY);
        assertThat(order.getTransactionId()).isEqualTo(razorpayPaymentId);
        assertThat(order.getPaymentVerifiedAt()).isNotNull();
        verify(orderRepository, times(1)).save(order);
    }

    @Test
    void testProcessWebhook_OrderPaid_Success() throws Exception {
        String razorpayOrderId = "order_O888888888";
        String razorpayPaymentId = "pay_P777777777";
        String payload = "{\n" +
                "  \"event\": \"order.paid\",\n" +
                "  \"payload\": {\n" +
                "    \"order\": {\n" +
                "      \"entity\": {\n" +
                "        \"id\": \"" + razorpayOrderId + "\",\n" +
                "        \"receipt\": \"LH-20260826-0002\"\n" +
                "      }\n" +
                "    },\n" +
                "    \"payment\": {\n" +
                "      \"entity\": {\n" +
                "        \"id\": \"" + razorpayPaymentId + "\",\n" +
                "        \"order_id\": \"" + razorpayOrderId + "\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";

        String signature = calculateHmacSha256(payload, webhookSecret);

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .orderNumber("LH-20260826-0002")
                .razorpayOrderId(razorpayOrderId)
                .paymentStatus(PaymentStatus.PENDING)
                .status(OrderStatus.PENDING)
                .build();

        when(orderRepository.findByRazorpayOrderId(razorpayOrderId)).thenReturn(Optional.of(order));

        razorpayService.processWebhook(payload, signature);

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getPaymentMethod()).isEqualTo(PaymentMethod.RAZORPAY);
        verify(orderRepository, times(1)).save(order);
    }

    @Test
    void testProcessWebhook_DuplicateWebhook_Idempotent() throws Exception {
        String razorpayOrderId = "order_O123456789";
        String razorpayPaymentId = "pay_P987654321";
        String payload = "{\n" +
                "  \"event\": \"payment.captured\",\n" +
                "  \"payload\": {\n" +
                "    \"payment\": {\n" +
                "      \"entity\": {\n" +
                "        \"id\": \"" + razorpayPaymentId + "\",\n" +
                "        \"order_id\": \"" + razorpayOrderId + "\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";

        String signature = calculateHmacSha256(payload, webhookSecret);

        LocalDateTime initialVerifiedAt = LocalDateTime.now().minusMinutes(5);
        Order alreadyPaidOrder = Order.builder()
                .id(UUID.randomUUID())
                .orderNumber("LH-20260826-0001")
                .razorpayOrderId(razorpayOrderId)
                .paymentStatus(PaymentStatus.PAID)
                .status(OrderStatus.CONFIRMED)
                .paymentMethod(PaymentMethod.RAZORPAY)
                .transactionId(razorpayPaymentId)
                .paymentVerifiedAt(initialVerifiedAt)
                .build();

        when(orderRepository.findByRazorpayOrderId(razorpayOrderId)).thenReturn(Optional.of(alreadyPaidOrder));

        razorpayService.processWebhook(payload, signature);

        // Should NOT perform duplicate save on already paid order
        verify(orderRepository, never()).save(alreadyPaidOrder);
        assertThat(alreadyPaidOrder.getPaymentVerifiedAt()).isEqualTo(initialVerifiedAt);
    }

    @Test
    void testProcessWebhook_InvalidSignature_ThrowsException() {
        String payload = "{\"event\": \"payment.captured\"}";
        String invalidSignature = "invalid_signature_hex_1234567890abcdef";

        assertThrows(InvalidOperationException.class, () ->
                razorpayService.processWebhook(payload, invalidSignature));

        verify(orderRepository, never()).findByRazorpayOrderId(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void testProcessWebhook_PaymentFailed_UpdatesStatus() throws Exception {
        String razorpayOrderId = "order_O_failed_123";
        String payload = "{\n" +
                "  \"event\": \"payment.failed\",\n" +
                "  \"payload\": {\n" +
                "    \"payment\": {\n" +
                "      \"entity\": {\n" +
                "        \"id\": \"pay_failed_1\",\n" +
                "        \"order_id\": \"" + razorpayOrderId + "\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";

        String signature = calculateHmacSha256(payload, webhookSecret);

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .orderNumber("LH-20260826-0003")
                .razorpayOrderId(razorpayOrderId)
                .paymentStatus(PaymentStatus.PENDING)
                .status(OrderStatus.PENDING)
                .build();

        when(orderRepository.findByRazorpayOrderId(razorpayOrderId)).thenReturn(Optional.of(order));

        razorpayService.processWebhook(payload, signature);

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        verify(orderRepository, times(1)).save(order);
    }

    @Test
    void testProcessWebhook_PaymentFailed_DoesNotDowngradeAlreadyPaidOrder() throws Exception {
        String razorpayOrderId = "order_O_paid_123";
        String payload = "{\n" +
                "  \"event\": \"payment.failed\",\n" +
                "  \"payload\": {\n" +
                "    \"payment\": {\n" +
                "      \"entity\": {\n" +
                "        \"id\": \"pay_failed_subsequent\",\n" +
                "        \"order_id\": \"" + razorpayOrderId + "\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";

        String signature = calculateHmacSha256(payload, webhookSecret);

        Order paidOrder = Order.builder()
                .id(UUID.randomUUID())
                .orderNumber("LH-20260826-0004")
                .razorpayOrderId(razorpayOrderId)
                .paymentStatus(PaymentStatus.PAID)
                .status(OrderStatus.CONFIRMED)
                .build();

        when(orderRepository.findByRazorpayOrderId(razorpayOrderId)).thenReturn(Optional.of(paidOrder));

        razorpayService.processWebhook(payload, signature);

        // Must maintain PAID status
        assertThat(paidOrder.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderRepository, never()).save(paidOrder);
    }

    @Test
    void testProcessWebhook_UnknownOrder_SafelyIgnored() throws Exception {
        String razorpayOrderId = "order_unknown_999";
        String payload = "{\n" +
                "  \"event\": \"payment.captured\",\n" +
                "  \"payload\": {\n" +
                "    \"payment\": {\n" +
                "      \"entity\": {\n" +
                "        \"id\": \"pay_unknown_999\",\n" +
                "        \"order_id\": \"" + razorpayOrderId + "\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";

        String signature = calculateHmacSha256(payload, webhookSecret);

        when(orderRepository.findByRazorpayOrderId(razorpayOrderId)).thenReturn(Optional.empty());

        // Should execute cleanly without throwing an exception or creating fake records
        razorpayService.processWebhook(payload, signature);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void testVerifyPayment_WhenAlreadyPaidByWebhook_SucceedsGracefully() {
        String orderNumber = "LH-20260826-0005";
        Order alreadyPaidOrder = Order.builder()
                .id(UUID.randomUUID())
                .orderNumber(orderNumber)
                .paymentStatus(PaymentStatus.PAID)
                .status(OrderStatus.CONFIRMED)
                .build();

        when(orderRepository.findByOrderNumber(orderNumber)).thenReturn(Optional.of(alreadyPaidOrder));

        RazorpayVerifyRequest request = RazorpayVerifyRequest.builder()
                .internalOrderId(orderNumber)
                .razorpayOrderId("order_123")
                .razorpayPaymentId("pay_123")
                .razorpaySignature("sig_123")
                .build();

        // Should return gracefully without throwing InvalidOperationException
        razorpayService.verifyPayment(request);

        verify(orderRepository, never()).save(alreadyPaidOrder);
    }

    @Test
    void testProcessWebhook_WithMockAddressAndUnknownEntities_SafelyProcessed() throws Exception {
        // Exact regression test payload representing Razorpay dashboard test webhook containing mock non-UUID strings
        String payload = "{\n" +
                "  \"entity\": \"event\",\n" +
                "  \"account_id\": \"acc_mock_1234\",\n" +
                "  \"event\": \"payment.captured\",\n" +
                "  \"contains\": [\"payment\"],\n" +
                "  \"payload\": {\n" +
                "    \"payment\": {\n" +
                "      \"entity\": {\n" +
                "        \"id\": \"pay_mock_9999\",\n" +
                "        \"order_id\": \"order_mock_5555\",\n" +
                "        \"notes\": {\n" +
                "          \"address_id\": \"addr_mock_4741\",\n" +
                "          \"user_id\": \"usr_mock_1111\",\n" +
                "          \"cart_id\": \"crt_mock_2222\"\n" +
                "        }\n" +
                "      }\n" +
                "    },\n" +
                "    \"order\": {\n" +
                "      \"entity\": {\n" +
                "        \"id\": \"order_mock_5555\",\n" +
                "        \"receipt\": \"addr_mock_4741\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";

        String signature = calculateHmacSha256(payload, webhookSecret);

        when(orderRepository.findByRazorpayOrderId("order_mock_5555")).thenReturn(Optional.empty());
        when(orderRepository.findByOrderNumber("addr_mock_4741")).thenReturn(Optional.empty());

        // Must process cleanly without throwing UUID deserialization or IllegalArgumentException
        razorpayService.processWebhook(payload, signature);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void testProcessWebhook_WithMockAddressNotes_AndMatchingRazorpayOrderId_Success() throws Exception {
        String razorpayOrderId = "order_O_real_999";
        String razorpayPaymentId = "pay_P_real_888";
        String payload = "{\n" +
                "  \"event\": \"payment.captured\",\n" +
                "  \"payload\": {\n" +
                "    \"payment\": {\n" +
                "      \"entity\": {\n" +
                "        \"id\": \"" + razorpayPaymentId + "\",\n" +
                "        \"order_id\": \"" + razorpayOrderId + "\",\n" +
                "        \"notes\": {\n" +
                "          \"address_id\": \"addr_mock_4741\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";

        String signature = calculateHmacSha256(payload, webhookSecret);

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .orderNumber("LH-20260826-9999")
                .razorpayOrderId(razorpayOrderId)
                .paymentStatus(PaymentStatus.PENDING)
                .status(OrderStatus.PENDING)
                .build();

        when(orderRepository.findByRazorpayOrderId(razorpayOrderId)).thenReturn(Optional.of(order));

        razorpayService.processWebhook(payload, signature);

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getTransactionId()).isEqualTo(razorpayPaymentId);
        verify(orderRepository, times(1)).save(order);
    }
}
