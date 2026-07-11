package com.lemonacademy.ecommerce.service;

import java.util.UUID;

import com.lemonacademy.ecommerce.dto.OrderRequest;
import com.lemonacademy.ecommerce.dto.OrderResponse;
import com.lemonacademy.ecommerce.dto.PageResponseDto;
import com.lemonacademy.ecommerce.entity.*;
import com.lemonacademy.ecommerce.exception.InsufficientStockException;
import com.lemonacademy.ecommerce.exception.InvalidOperationException;
import com.lemonacademy.ecommerce.exception.ResourceNotFoundException;
import com.lemonacademy.ecommerce.exception.UnauthorizedAccessException;
import com.lemonacademy.ecommerce.repository.AddressRepository;
import com.lemonacademy.ecommerce.repository.CartRepository;
import com.lemonacademy.ecommerce.repository.OrderRepository;
import com.lemonacademy.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private com.lemonacademy.ecommerce.shipping.service.IcarryShipmentService icarryShipmentService;

    @Mock
    private com.lemonacademy.ecommerce.shipping.service.IcarryTrackingService icarryTrackingService;

    @InjectMocks
    private OrderService orderService;

    private User user;
    private Address address;
    private Cart cart;
    private Product product;
    private CartItem cartItem;
    private Order order;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))
                .email("test@example.com")
                .role(Role.CUSTOMER)
                .build();

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        address = Address.builder()
                .id(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))
                .user(user)
                .addressLine1("123 Test St")
                .city("Test City")
                .build();

        product = Product.builder()
                .id(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))
                .name("Laptop")
                .price(new BigDecimal("1000.00"))
                .stock(10)
                .active(true)
                .build();

        cart = Cart.builder()
                .id(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))
                .user(user)
                .items(new ArrayList<>())
                .build();

        cartItem = CartItem.builder()
                .id(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))
                .cart(cart)
                .product(product)
                .quantity(2)
                .build();

        order = new Order();
        order.setId(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));
        order.setUser(user);
        order.setAddress(address);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(new BigDecimal("2000.00"));
        
        OrderItem orderItem = OrderItem.builder()
                .id(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))
                .order(order)
                .product(product)
                .quantity(2)
                .price(product.getPrice())
                .build();
                
        order.setItems(Collections.singletonList(orderItem));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createOrder_Success() {
        cart.getItems().add(cartItem);
        OrderRequest request = new OrderRequest();
        request.setAddressId(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));

        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
        when(addressRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.of(address));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArguments()[0]);

        OrderResponse response = orderService.createOrder(request);

        assertThat(response).isNotNull();
        assertThat(response.getTotalAmount()).isEqualTo(new BigDecimal("2000.00"));
        assertThat(cart.getItems()).isEmpty();
        verify(productRepository, times(1)).save(product);
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(cartRepository, times(1)).save(cart);
    }

    @Test
    void createOrder_CartEmpty() {
        OrderRequest request = new OrderRequest();

        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart)); // empty items list

        assertThrows(InvalidOperationException.class, () -> orderService.createOrder(request));
    }

    @Test
    void createOrder_AddressNotFound() {
        cart.getItems().add(cartItem);
        OrderRequest request = new OrderRequest();
        request.setAddressId(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));

        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
        when(addressRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.createOrder(request));
    }

    @Test
    void createOrder_AddressUnauthorized() {
        cart.getItems().add(cartItem);
        
        User otherUser = User.builder().id(UUID.fromString("df4382cf-73c7-35ab-965a-b690f63e0acf")).build();
        address.setUser(otherUser);
        
        OrderRequest request = new OrderRequest();
        request.setAddressId(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));

        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
        when(addressRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.of(address));

        assertThrows(UnauthorizedAccessException.class, () -> orderService.createOrder(request));
    }

    @Test
    void createOrder_ProductInactive() {
        product.setActive(false);
        cart.getItems().add(cartItem);
        
        OrderRequest request = new OrderRequest();
        request.setAddressId(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));

        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
        when(addressRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.of(address));

        assertThrows(InvalidOperationException.class, () -> orderService.createOrder(request));
    }

    @Test
    void createOrder_InsufficientStock() {
        product.setStock(1); // Requested is 2
        cart.getItems().add(cartItem);
        
        OrderRequest request = new OrderRequest();
        request.setAddressId(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"));

        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
        when(addressRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.of(address));

        assertThrows(InsufficientStockException.class, () -> orderService.createOrder(request));
    }

    @Test
    void getMyOrders_Success() {
        PageRequest pageRequest = PageRequest.of(0, 10);
        Page<Order> orderPage = new PageImpl<>(Collections.singletonList(order), pageRequest, 1);
        when(orderRepository.findByUserOrderByCreatedAtDesc(user, pageRequest)).thenReturn(orderPage);

        PageResponseDto<OrderResponse> response = orderService.getMyOrders(pageRequest);

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getPageNumber()).isEqualTo(0);
        assertThat(response.getPageSize()).isEqualTo(10);
        assertThat(response.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getOrderDetails_Owned_Success() {
        when(orderRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrderDetails("23db3d7a-683b-372b-8036-95da3ae5c542");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("LH-20260711-A3F8");
    }

    @Test
    void getOrderDetails_Admin_Success() {
        User adminUser = User.builder().id(UUID.fromString("df4382cf-73c7-35ab-965a-b690f63e0acf")).role(Role.ADMIN).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(adminUser, null, adminUser.getAuthorities()));

        when(orderRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrderDetails("23db3d7a-683b-372b-8036-95da3ae5c542");

        assertThat(response).isNotNull();
    }

    @Test
    void getOrderDetails_Unauthorized() {
        User otherUser = User.builder().id(UUID.fromString("df4382cf-73c7-35ab-965a-b690f63e0acf")).role(Role.CUSTOMER).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(otherUser, null, otherUser.getAuthorities()));

        when(orderRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.of(order));

        assertThrows(UnauthorizedAccessException.class, () -> orderService.getOrderDetails("23db3d7a-683b-372b-8036-95da3ae5c542"));
    }

    @Test
    void getOrderDetails_NotFound() {
        when(orderRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.getOrderDetails("23db3d7a-683b-372b-8036-95da3ae5c542"));
    }

    @Test
    void getAllOrders_Success() {
        when(orderRepository.findAllByOrderByCreatedAtDesc()).thenReturn(Collections.singletonList(order));

        java.util.List<OrderResponse> response = orderService.getAllOrders();

        assertThat(response).isNotNull();
        assertThat(response).hasSize(1);
    }

    @Test
    void updateOrderStatus_Success() {
        when(orderRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArguments()[0]);

        OrderResponse response = orderService.updateOrderStatus(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"), OrderStatus.SHIPPED);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void updateOrderStatus_NotFound() {
        when(orderRepository.findById(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.updateOrderStatus(UUID.fromString("23db3d7a-683b-372b-8036-95da3ae5c542"), OrderStatus.SHIPPED));
    }
}
