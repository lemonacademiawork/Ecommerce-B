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
    private final ProductVariantRepository productVariantRepository;

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

        // 1. Determine items source: database cart OR request body items
        // Try database cart first
        Cart cart = cartRepository.findByUser(user).orElse(null);
        boolean hasDbCart = cart != null && !cart.getItems().isEmpty();
        boolean hasRequestItems = request.getItems() != null && !request.getItems().isEmpty();

        if (!hasDbCart && !hasRequestItems) {
            throw new InvalidOperationException("Cart is empty");
        }

        // Build a unified list of (product, variant, quantity) tuples to process
        List<CartItemTuple> itemsToProcess = new ArrayList<>();

        if (hasDbCart) {
            // Use database cart items
            for (CartItem cartItem : cart.getItems()) {
                itemsToProcess.add(new CartItemTuple(
                        cartItem.getProduct(),
                        cartItem.getProductVariant(),
                        cartItem.getQuantity()));
            }
        } else {
            // Use request body items
            for (OrderItemRequest itemReq : request.getItems()) {
                Product product = productRepository.findById(itemReq.getProductId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Product not found with id: " + itemReq.getProductId()));

                ProductVariant variant = null;
                if (itemReq.getVariantId() != null) {
                    variant = productVariantRepository.findById(itemReq.getVariantId())
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "Variant not found with id: " + itemReq.getVariantId()));
                    if (!variant.getProduct().getId().equals(product.getId())) {
                        throw new InvalidOperationException("Variant does not belong to product: " + product.getName());
                    }
                }

                itemsToProcess.add(new CartItemTuple(product, variant, itemReq.getQuantity()));
            }
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
        int maxLen = 0, maxBre = 0;
        long totalVolume = 0;

        Order order = new Order();
        order.setUser(user);
        order.setAddress(address);
        order.setStatus(OrderStatus.PAYMENT_PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);

        for (CartItemTuple tuple : itemsToProcess) {
            Product product = tuple.product;

            // Validate product is active
            if (!Boolean.TRUE.equals(product.getActive())) {
                throw new InvalidOperationException("Cannot order inactive product: " + product.getName());
            }

            ProductVariant variant = tuple.variant;

            // Validate stock
            int availableStock = variant != null ? variant.getStock() : product.getStock();
            if (tuple.quantity > availableStock) {
                throw new InsufficientStockException(
                        "Insufficient stock for product " + product.getName() + 
                        (variant != null ? " (" + variant.getVariantName() + ")" : "") + 
                        ". Available: " + availableStock);
            }

            // Reduce Stock
            if (variant != null) {
                variant.setStock(variant.getStock() - tuple.quantity);
                productVariantRepository.save(variant);
            } else {
                product.setStock(product.getStock() - tuple.quantity);
                productRepository.save(product);
            }

            BigDecimal itemPrice = variant != null ? variant.getPrice() : product.getPrice();
            // Compute subtotal and add to order items
            BigDecimal itemSubtotal = itemPrice.multiply(BigDecimal.valueOf(tuple.quantity));
            subtotal = subtotal.add(itemSubtotal);

            // Accumulate weight and max dimensions
            int l = product.getLength() != null ? product.getLength() : 10;
            int b = product.getBreadth() != null ? product.getBreadth() : 10;
            int h = product.getHeight() != null ? product.getHeight() : 10;
            
            int w = variant != null && variant.getWeight() != null ? variant.getWeight() : (product.getWeight() != null ? product.getWeight() : 500);
            
            totalWeight += w * tuple.quantity;
            totalVolume += (long) l * b * h * tuple.quantity;
            maxLen = Math.max(maxLen, l);
            maxBre = Math.max(maxBre, b);

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .productVariant(variant)
                    .variantName(variant != null ? variant.getVariantName() : null)
                    .quantity(tuple.quantity)
                    .price(itemPrice)
                    .build();

            orderItems.add(orderItem);
        }

        // 4. Calculate Shipping Estimate dynamically
        int finalLen = maxLen > 0 ? maxLen : 10;
        int finalBre = maxBre > 0 ? maxBre : 10;
        int finalHei = (int) Math.max(10, Math.ceil((double) totalVolume / (finalLen * finalBre)));
        
        BigDecimal shippingCharge = BigDecimal.ZERO;
        try {
            ShippingEstimateRequest estimateRequest = ShippingEstimateRequest.builder()
                    .weight(totalWeight)
                    .length(finalLen)
                    .breadth(finalBre)
                    .height(finalHei)
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
        order.setLength(finalLen);
        order.setBreadth(finalBre);
        order.setHeight(finalHei);

        // 6. Clear Cart items (only if database cart was the source)
        if (hasDbCart && cart != null) {
            cart.getItems().clear();
            cartRepository.save(cart);
        }

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
                        .productName(sanitizeForExternalApi(item.getProduct().getName()))
                        .imageUrl(item.getProduct().getImageUrl())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .subtotal(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                        .variantId(item.getProductVariant() != null ? item.getProductVariant().getId() : null)
                        .variantName(sanitizeForExternalApi(item.getVariantName()))
                        .variant(item.getProductVariant() != null ? convertToVariantDto(item.getProductVariant()) : null)
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
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .transactionId(order.getTransactionId())
                .paymentScreenshotUrl(order.getPaymentScreenshotUrl())
                .build();
    }

    private ProductVariantResponseDto convertToVariantDto(ProductVariant v) {
        return ProductVariantResponseDto.builder()
                .id(v.getId())
                .productId(v.getProduct().getId())
                .variantName(v.getVariantName())
                .weight(v.getWeight())
                .weightUnit(v.getWeightUnit())
                .volume(v.getVolume())
                .volumeUnit(v.getVolumeUnit())
                .sizeLabel(v.getSizeLabel())
                .price(v.getPrice())
                .discountedPrice(v.getDiscountedPrice())
                .stock(v.getStock())
                .sku(v.getSku())
                .barcode(v.getBarcode())
                .status(v.getStatus())
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .build();
    }

    private String sanitizeForExternalApi(String input) {
        if (input == null) return null;
        return input
                .replace("\u2013", "-") // en-dash
                .replace("\u2014", "-") // em-dash
                .replace("\u2018", "'") // left single quote
                .replace("\u2019", "'") // right single quote
                .replace("\u201c", "\"") // left double quote
                .replace("\u201d", "\"") // right double quote
                .replaceAll("[^\\x00-\\x7F]", ""); // strip any remaining non-ASCII characters
    }

    /**
     * Simple tuple to unify cart items from either the database cart or the request body.
     */
    private static class CartItemTuple {
        final Product product;
        final ProductVariant variant;
        final int quantity;

        CartItemTuple(Product product, ProductVariant variant, int quantity) {
            this.product = product;
            this.variant = variant;
            this.quantity = quantity;
        }
    }
}
