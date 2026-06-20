package com.lemonacademy.ecommerce.service;

import com.lemonacademy.ecommerce.dto.*;
import com.lemonacademy.ecommerce.entity.*;
import com.lemonacademy.ecommerce.exception.*;
import com.lemonacademy.ecommerce.repository.*;
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

        return convertToOrderResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders() {
        User user = getAuthenticatedUser();
        return orderRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::convertToOrderResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderDetails(Long id) {
        User user = getAuthenticatedUser();
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));

        // Validate Ownership: Admin can access any order, Customer can only access their own
        boolean isAdmin = user.getRole() == Role.ADMIN;
        if (!isAdmin && !order.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedAccessException("You are not authorized to view this order");
        }

        return convertToOrderResponse(order);
    }

    // --- Admin Operations ---

    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::convertToOrderResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long id, OrderStatus status) {
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
                .build();
    }
}
