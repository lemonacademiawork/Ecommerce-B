package com.lemonacademy.ecommerce.service;

import com.lemonacademy.ecommerce.dto.AddToCartRequest;
import com.lemonacademy.ecommerce.dto.CartResponse;
import com.lemonacademy.ecommerce.dto.UpdateCartItemRequest;
import com.lemonacademy.ecommerce.entity.Cart;
import com.lemonacademy.ecommerce.entity.CartItem;
import com.lemonacademy.ecommerce.entity.Product;
import com.lemonacademy.ecommerce.entity.Role;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.exception.InsufficientStockException;
import com.lemonacademy.ecommerce.exception.InvalidOperationException;
import com.lemonacademy.ecommerce.exception.ResourceNotFoundException;
import com.lemonacademy.ecommerce.exception.UnauthorizedAccessException;
import com.lemonacademy.ecommerce.repository.CartItemRepository;
import com.lemonacademy.ecommerce.repository.CartRepository;
import com.lemonacademy.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CartService cartService;

    private User user;
    private Product product;
    private Cart cart;
    private CartItem cartItem;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .email("test@example.com")
                .role(Role.CUSTOMER)
                .build();

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        product = Product.builder()
                .id(1L)
                .name("Test Product")
                .price(new BigDecimal("100.00"))
                .stock(10)
                .active(true)
                .build();

        cart = Cart.builder()
                .id(1L)
                .user(user)
                .items(new ArrayList<>())
                .build();

        cartItem = CartItem.builder()
                .id(1L)
                .cart(cart)
                .product(product)
                .quantity(2)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void addToCart_NewItem_Success() {
        AddToCartRequest request = new AddToCartRequest();
        request.setProductId(1L);
        request.setQuantity(2);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(cartRepository.findByUser(any(User.class))).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartAndProduct(any(Cart.class), any(Product.class))).thenReturn(Optional.empty());

        CartResponse response = cartService.addToCart(request);

        assertThat(response).isNotNull();
        verify(cartItemRepository, times(1)).save(any(CartItem.class));
    }

    @Test
    void addToCart_ExistingItem_Success() {
        AddToCartRequest request = new AddToCartRequest();
        request.setProductId(1L);
        request.setQuantity(3);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(cartRepository.findByUser(any(User.class))).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartAndProduct(any(Cart.class), any(Product.class))).thenReturn(Optional.of(cartItem));

        CartResponse response = cartService.addToCart(request);

        assertThat(response).isNotNull();
        assertThat(cartItem.getQuantity()).isEqualTo(5); // 2 + 3
        verify(cartItemRepository, times(1)).save(cartItem);
    }

    @Test
    void addToCart_ProductNotFound() {
        AddToCartRequest request = new AddToCartRequest();
        request.setProductId(1L);

        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cartService.addToCart(request));
    }

    @Test
    void addToCart_ProductInactive() {
        product.setActive(false);
        AddToCartRequest request = new AddToCartRequest();
        request.setProductId(1L);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThrows(InvalidOperationException.class, () -> cartService.addToCart(request));
    }

    @Test
    void addToCart_InsufficientStock() {
        AddToCartRequest request = new AddToCartRequest();
        request.setProductId(1L);
        request.setQuantity(15); // > 10

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(cartRepository.findByUser(any(User.class))).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartAndProduct(any(Cart.class), any(Product.class))).thenReturn(Optional.empty());

        assertThrows(InsufficientStockException.class, () -> cartService.addToCart(request));
    }

    @Test
    void getCart_Success() {
        when(cartRepository.findByUser(any(User.class))).thenReturn(Optional.of(cart));
        cart.getItems().add(cartItem);

        CartResponse response = cartService.getCart();

        assertThat(response).isNotNull();
        assertThat(response.getTotalItems()).isEqualTo(2);
        assertThat(response.getTotalAmount()).isEqualTo(new BigDecimal("200.00")); // 100 * 2
    }

    @Test
    void getCart_CreateNew_Success() {
        when(cartRepository.findByUser(any(User.class))).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        CartResponse response = cartService.getCart();

        assertThat(response).isNotNull();
        assertThat(response.getItems()).isEmpty();
    }

    @Test
    void updateCartItem_Success() {
        UpdateCartItemRequest request = new UpdateCartItemRequest();
        request.setCartItemId(1L);
        request.setQuantity(5);

        when(cartItemRepository.findById(1L)).thenReturn(Optional.of(cartItem));
        cart.getItems().add(cartItem);

        CartResponse response = cartService.updateCartItem(request);

        assertThat(response).isNotNull();
        assertThat(cartItem.getQuantity()).isEqualTo(5);
        verify(cartItemRepository, times(1)).save(cartItem);
    }

    @Test
    void updateCartItem_NotFound() {
        UpdateCartItemRequest request = new UpdateCartItemRequest();
        request.setCartItemId(1L);

        when(cartItemRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cartService.updateCartItem(request));
    }

    @Test
    void updateCartItem_Unauthorized() {
        User otherUser = User.builder().id(2L).build();
        cart.setUser(otherUser);

        UpdateCartItemRequest request = new UpdateCartItemRequest();
        request.setCartItemId(1L);

        when(cartItemRepository.findById(1L)).thenReturn(Optional.of(cartItem));

        assertThrows(UnauthorizedAccessException.class, () -> cartService.updateCartItem(request));
    }

    @Test
    void updateCartItem_InvalidQuantity() {
        UpdateCartItemRequest request = new UpdateCartItemRequest();
        request.setCartItemId(1L);
        request.setQuantity(0);

        when(cartItemRepository.findById(1L)).thenReturn(Optional.of(cartItem));

        assertThrows(InvalidOperationException.class, () -> cartService.updateCartItem(request));
    }

    @Test
    void updateCartItem_InsufficientStock() {
        UpdateCartItemRequest request = new UpdateCartItemRequest();
        request.setCartItemId(1L);
        request.setQuantity(15);

        when(cartItemRepository.findById(1L)).thenReturn(Optional.of(cartItem));

        assertThrows(InsufficientStockException.class, () -> cartService.updateCartItem(request));
    }

    @Test
    void removeCartItem_Success() {
        when(cartItemRepository.findById(1L)).thenReturn(Optional.of(cartItem));
        cart.getItems().add(cartItem);

        CartResponse response = cartService.removeCartItem(1L);

        assertThat(response).isNotNull();
        assertThat(cart.getItems()).isEmpty();
        verify(cartItemRepository, times(1)).delete(cartItem);
    }

    @Test
    void removeCartItem_Unauthorized() {
        User otherUser = User.builder().id(2L).build();
        cart.setUser(otherUser);

        when(cartItemRepository.findById(1L)).thenReturn(Optional.of(cartItem));

        assertThrows(UnauthorizedAccessException.class, () -> cartService.removeCartItem(1L));
    }

    @Test
    void clearCart_Success() {
        when(cartRepository.findByUser(any(User.class))).thenReturn(Optional.of(cart));
        cart.getItems().add(cartItem);

        CartResponse response = cartService.clearCart();

        assertThat(response).isNotNull();
        assertThat(cart.getItems()).isEmpty();
        verify(cartRepository, times(1)).save(cart);
    }

    @Test
    void clearCart_NotFound() {
        when(cartRepository.findByUser(any(User.class))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cartService.clearCart());
    }
}
