package com.lemonacademy.ecommerce.service;

import java.util.UUID;

import com.lemonacademy.ecommerce.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.lemonacademy.ecommerce.entity.*;
import com.lemonacademy.ecommerce.exception.*;
import com.lemonacademy.ecommerce.repository.*;
import com.lemonacademy.ecommerce.shipping.service.IcarryShipmentService;
import com.lemonacademy.ecommerce.shipping.service.IcarryTrackingService;
import com.lemonacademy.ecommerce.shipping.service.IcarryEstimateService;
import com.lemonacademy.ecommerce.shipping.dto.TrackingResponse;
import com.lemonacademy.ecommerce.shipping.dto.ShippingEstimateRequest;
import com.lemonacademy.ecommerce.shipping.dto.CourierEstimateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final AddressRepository addressRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final IcarryShipmentService icarryShipmentService;
    private final IcarryTrackingService icarryTrackingService;
    private final IcarryEstimateService icarryEstimateService;

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (User) authentication.getPrincipal();
    }

    /**
     * Creates an order. The order save and the iCarry booking are in separate
     * transactions so that a shipping API failure does not roll back the order.
     */
    public OrderResponse createOrder(OrderRequest request) {
        // Step 1: Save order in its own transaction
        Order savedOrder = saveOrderInTransaction(request);

        // Step 2: Attempt iCarry booking outside the order transaction
        try {
            Order booked = icarryShipmentService.bookShipmentForOrder(savedOrder);
            if (booked != null) {
                savedOrder = booked;
            }
        } catch (Exception e) {
            log.warn("iCarry booking failed for order {}: {}. Order is saved with PENDING status.",
                     savedOrder.getId(), e.getMessage());
            // Order is already persisted — just mark shipment as pending
            try {
                savedOrder.setShipmentStatus("PENDING_BOOKING");
                savedOrder = orderRepository.save(savedOrder);
            } catch (Exception inner) {
                // Log but don't fail — the order itself is safe
                log.error("Failed to update shipment status for order {}: {}", savedOrder.getId(), inner.getMessage());
            }
        }

        return convertToOrderResponse(savedOrder);
    }

    @Transactional
    public Order saveOrderInTransaction(OrderRequest request) {
        User user = getAuthenticatedUser();

        // 1. Fetch Cart and validate it's not empty
        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new InvalidOperationException("Cart is empty"));

        if (cart.getItems().isEmpty()) {
            throw new InvalidOperationException("Cart is empty");
        }

        // 2. Fetch Address & Validate Ownership
        Address address = addressRepository.findById(request.getAddressId())
                .orElseThrow(() -> new ResourceNotFoundException("Address not found with id: " + request.getAddressId()));

        if (!address.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedAccessException("Address does not belong to the authenticated user");
        }

        // 3. Validate Stock & Prepare Order Items
        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        int totalWeight = 0;
        int maxLen = 0, maxBre = 0, maxHei = 0;

        Order order = new Order();
        order.setUser(user);
        order.setAddress(address);
        order.setStatus(OrderStatus.PENDING);

        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();

            // Validate product is active
            if (!Boolean.TRUE.equals(product.getActive())) {
                throw new InvalidOperationException("Cannot order inactive product: " + product.getName());
            }

            // Validate stock
            if (cartItem.getQuantity() > product.getStock()) {
                throw new InsufficientStockException(
                        "Insufficient stock for product " + product.getName() + ". Available: " + product.getStock());
            }

            // Reduce Stock
            product.setStock(product.getStock() - cartItem.getQuantity());
            productRepository.save(product);

            // Compute subtotal and add to order items
            BigDecimal itemSubtotal = product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            subtotal = subtotal.add(itemSubtotal);

            // Accumulate weight and max dimensions
            totalWeight += (product.getWeight() != null ? product.getWeight() : 500) * cartItem.getQuantity();
            maxLen = Math.max(maxLen, product.getLength() != null ? product.getLength() : 10);
            maxBre = Math.max(maxBre, product.getBreadth() != null ? product.getBreadth() : 10);
            maxHei = Math.max(maxHei, product.getHeight() != null ? product.getHeight() : 10);

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(cartItem.getQuantity())
                    .price(product.getPrice())
                    .build();

            orderItems.add(orderItem);
        }

        // 4. Calculate Shipping Estimate dynamically
        BigDecimal shippingCharge = BigDecimal.ZERO;
        try {
            ShippingEstimateRequest estimateRequest = ShippingEstimateRequest.builder()
                    .weight(totalWeight)
                    .length(maxLen)
                    .breadth(maxBre)
                    .height(maxHei)
                    .originPincode("284003") // Default origin from properties
                    .destinationPincode(address.getPincode())
                    .parcelValue(subtotal)
                    .parcelType("P")
                    .shipmentMode("S")
                    .build();

            List<CourierEstimateResponse> estimates = icarryEstimateService.getEstimate(estimateRequest);
            if (estimates != null && !estimates.isEmpty()) {
                // Find the lowest rate
                shippingCharge = estimates.stream()
                        .map(CourierEstimateResponse::getRate)
                        .min(BigDecimal::compareTo)
                        .orElse(BigDecimal.valueOf(50));
            } else {
                shippingCharge = BigDecimal.valueOf(50); // fallback
            }
        } catch (Exception e) {
            log.error("Failed to get shipping estimate, using fallback: {}", e.getMessage());
            shippingCharge = BigDecimal.valueOf(50); // fallback
        }

        // 5. Check and apply coupon
        BigDecimal discountAmount = BigDecimal.ZERO;
        Coupon appliedCoupon = null;
        if (request.getCouponCode() != null && !request.getCouponCode().isBlank()) {
            appliedCoupon = couponRepository.findByCode(request.getCouponCode().trim().toUpperCase())
                    .orElseThrow(() -> new InvalidOperationException("Invalid coupon code"));

            if (!Boolean.TRUE.equals(appliedCoupon.getActive())) {
                throw new InvalidOperationException("Coupon is no longer active");
            }

            // First order only restriction: check if user has any orders
            boolean hasOrders = orderRepository.findByUserOrderByCreatedAtDesc(user, Pageable.unpaged()).hasContent();
            if (hasOrders) {
                throw new InvalidOperationException("This coupon is only valid for your first order.");
            }

            // Check if already used by user (just in case they cancel/retry, though first order check handles most of it)
            if (userCouponRepository.existsByUserAndCoupon(user, appliedCoupon)) {
                throw new InvalidOperationException("You have already used this coupon.");
            }

            // Calculate discount (discountPercentage is e.g. 5.0 for 5%)
            BigDecimal percentage = appliedCoupon.getDiscountPercentage().divide(BigDecimal.valueOf(100));
            discountAmount = subtotal.multiply(percentage);
        }

        BigDecimal totalAmount = subtotal.add(shippingCharge).subtract(discountAmount);

        order.setSubtotal(subtotal);
        order.setShippingCharge(shippingCharge);
        order.setDiscountAmount(discountAmount);
        order.setCouponCode(appliedCoupon != null ? appliedCoupon.getCode() : null);
        order.setTotalAmount(totalAmount);
        order.setItems(orderItems);
        order.setWeight(totalWeight);
        order.setLength(maxLen);
        order.setBreadth(maxBre);
        order.setHeight(maxHei);

        // 6. Clear Cart items (preserving the cart record itself)
        cart.getItems().clear();
        cartRepository.save(cart);

        // 7. Save Order and optionally UserCoupon
        Order savedOrder = orderRepository.save(order);

        if (appliedCoupon != null) {
            UserCoupon userCoupon = UserCoupon.builder()
                    .user(user)
                    .coupon(appliedCoupon)
                    .build();
            userCouponRepository.save(userCoupon);
        }

        return savedOrder;
    }

    @Transactional(readOnly = true)
    public PageResponseDto<OrderResponse> getMyOrders(Pageable pageable) {
        User user = getAuthenticatedUser();
        Page<Order> orderPage = orderRepository.findByUserOrderByCreatedAtDesc(user, pageable);
        return PageResponseDto.of(orderPage, this::convertToOrderResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderDetails(String id) {
        User user = getAuthenticatedUser();
        Order order = null;
        try {
            UUID uuid = UUID.fromString(id);
            order = orderRepository.findById(uuid).orElse(null);
        } catch (IllegalArgumentException e) {
            // Not a UUID, try orderNumber
        }
        
        if (order == null) {
            order = orderRepository.findByOrderNumber(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        }

        // Validate Ownership: Admin can access any order, Customer can only access their own
        boolean isAdmin = user.getRole() == Role.ADMIN;
        if (!isAdmin && !order.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedAccessException("You are not authorized to view this order");
        }

        OrderResponse response = convertToOrderResponse(order);
        
        // Fetch live tracking history for this order if it has an AWB number
        if (order.getAwbNumber() != null && !order.getAwbNumber().trim().isEmpty()) {
            try {
                TrackingResponse tracking = icarryTrackingService.trackShipment(order.getAwbNumber());
                if (tracking != null) {
                    response.setTrackingEvents(tracking.getEvents());
                    response.setShipmentStatus(tracking.getStatus());
                }
            } catch (Exception e) {
                // Ignore tracking fetch errors so the order details still load
            }
        }
        
        return response;
    }

    // --- Admin Operations ---

    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::convertToOrderResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public OrderResponse updateOrderStatus(String id, OrderStatus status) {
        Order order = null;
        try {
            UUID uuid = UUID.fromString(id);
            order = orderRepository.findById(uuid).orElse(null);
        } catch (IllegalArgumentException e) {
            // Not a UUID, try orderNumber
        }
        
        if (order == null) {
            order = orderRepository.findByOrderNumber(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        }

        order.setStatus(status);
        Order updated = orderRepository.save(order);
        return convertToOrderResponse(updated);
    }

    // --- Conversion Helpers ---

    private OrderResponse convertToOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .imageUrl(item.getProduct().getImageUrl())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .subtotal(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                        .build())
                .collect(Collectors.toList());

        AddressResponse addressResponse = AddressResponse.builder()
                .id(order.getAddress().getId())
                .fullName(order.getAddress().getFullName())
                .phone(order.getAddress().getPhone())
                .addressLine1(order.getAddress().getAddressLine1())
                .addressLine2(order.getAddress().getAddressLine2())
                .city(order.getAddress().getCity())
                .state(order.getAddress().getState())
                .pincode(order.getAddress().getPincode())
                .isDefault(order.getAddress().getIsDefault())
                .createdAt(order.getAddress().getCreatedAt())
                .build();

        String displayId = order.getOrderNumber() != null ? order.getOrderNumber() : (order.getId() != null ? order.getId().toString() : null);

        return OrderResponse.builder()
                .id(displayId)
                .orderNumber(displayId)
                .internalId(order.getId())
                .userId(order.getUser().getId())
                .address(addressResponse)
                .subtotal(order.getSubtotal())
                .totalAmount(order.getTotalAmount())
                .shippingCharge(order.getShippingCharge())
                .discountAmount(order.getDiscountAmount())
                .couponCode(order.getCouponCode())
                .status(order.getStatus())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .shipmentId(order.getShipmentId())
                .trackingNumber(order.getTrackingNumber())
                .awbNumber(order.getAwbNumber())
                .courierName(order.getCourierName())
                .shipmentStatus(order.getShipmentStatus())
                .shippingCharge(order.getShippingCharge())
                .estimatedDeliveryDate(order.getEstimatedDeliveryDate())
                .labelUrl(order.getLabelUrl())
                .pickupRequested(order.getPickupRequested())
                .pickupDate(order.getPickupDate())
                .reverseShipmentId(order.getReverseShipmentId())
                .lastTrackingSync(order.getLastTrackingSync())
                .weight(order.getWeight())
                .length(order.getLength())
                .breadth(order.getBreadth())
                .height(order.getHeight())
                .build();
    }
}
