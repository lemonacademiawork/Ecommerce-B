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
import com.lemonacademy.ecommerce.shipping.dto.TrackingResponse;
import lombok.RequiredArgsConstructor;
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
public class OrderService {

    private final OrderRepository orderRepository;
    private final AddressRepository addressRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final IcarryShipmentService icarryShipmentService;
    private final IcarryTrackingService icarryTrackingService;

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (User) authentication.getPrincipal();
    }

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
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
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

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
            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            totalAmount = totalAmount.add(subtotal);

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(cartItem.getQuantity())
                    .price(product.getPrice())
                    .build();

            orderItems.add(orderItem);
        }

        order.setTotalAmount(totalAmount);
        order.setItems(orderItems);

        // 4. Clear Cart items (preserving the cart record itself)
        cart.getItems().clear();
        cartRepository.save(cart);

        // 5. Save Order
        Order savedOrder = orderRepository.save(order);

        // 6. Automatically book shipment with iCarry
        try {
            Order booked = icarryShipmentService.bookShipmentForOrder(savedOrder);
            if (booked != null) {
                savedOrder = booked;
            }
        } catch (Exception e) {
            // Fallback: update status to PENDING_BOOKING so it can be retried manually
            savedOrder.setShipmentStatus("PENDING_BOOKING");
            savedOrder = orderRepository.save(savedOrder);
        }

        return convertToOrderResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public PageResponseDto<OrderResponse> getMyOrders(Pageable pageable) {
        User user = getAuthenticatedUser();
        Page<Order> orderPage = orderRepository.findByUserOrderByCreatedAtDesc(user, pageable);
        return PageResponseDto.of(orderPage, this::convertToOrderResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderDetails(UUID id) {
        User user = getAuthenticatedUser();
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));

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
    public OrderResponse updateOrderStatus(UUID id, OrderStatus status) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));

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

        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUser().getId())
                .address(addressResponse)
                .totalAmount(order.getTotalAmount())
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
