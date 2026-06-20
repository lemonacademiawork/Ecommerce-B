package com.lemonacademy.ecommerce.service;

import com.lemonacademy.ecommerce.dto.*;
import com.lemonacademy.ecommerce.entity.Cart;
import com.lemonacademy.ecommerce.entity.CartItem;
import com.lemonacademy.ecommerce.entity.Product;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.exception.InsufficientStockException;
import com.lemonacademy.ecommerce.exception.InvalidOperationException;
import com.lemonacademy.ecommerce.exception.ResourceNotFoundException;
import com.lemonacademy.ecommerce.exception.UnauthorizedAccessException;
import com.lemonacademy.ecommerce.repository.CartItemRepository;
import com.lemonacademy.ecommerce.repository.CartRepository;
import com.lemonacademy.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    @Transactional
    public CartResponse addToCart(AddToCartRequest request) {
        User user = getAuthenticatedUser();

        // Validate product exists
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + request.getProductId()));

        // Validate product is active
        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new InvalidOperationException("Cannot add inactive product to cart");
        }

        // Get or create cart
        Cart cart = cartRepository.findByUser(user)
                .orElseGet(() -> {
                    Cart newCart = Cart.builder()
                            .user(user)
                            .items(new ArrayList<>())
                            .build();
                    return cartRepository.save(newCart);
                });

        // Check if product already exists in cart
        Optional<CartItem> existingItem = cartItemRepository.findByCartAndProduct(cart, product);

        if (existingItem.isPresent()) {
            // Increase quantity
            CartItem cartItem = existingItem.get();
            int newQuantity = cartItem.getQuantity() + request.getQuantity();

            // Validate stock availability (total quantity in cart)
            if (newQuantity > product.getStock()) {
                throw new InsufficientStockException(
                        "Insufficient stock. Available: " + product.getStock()
                                + ", Already in cart: " + cartItem.getQuantity()
                                + ", Requested: " + request.getQuantity());
            }

            cartItem.setQuantity(newQuantity);
            cartItemRepository.save(cartItem);
        } else {
            // Validate stock availability
            if (request.getQuantity() > product.getStock()) {
                throw new InsufficientStockException(
                        "Insufficient stock. Available: " + product.getStock()
                                + ", Requested: " + request.getQuantity());
            }

            // Create new CartItem
            CartItem cartItem = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(request.getQuantity())
                    .build();
            cartItemRepository.save(cartItem);
            cart.getItems().add(cartItem);
        }

        return buildCartResponse(cart);
    }

    @Transactional(readOnly = true)
    public CartResponse getCart() {
        User user = getAuthenticatedUser();

        Cart cart = cartRepository.findByUser(user)
                .orElseGet(() -> {
                    Cart newCart = Cart.builder()
                            .user(user)
                            .items(new ArrayList<>())
                            .build();
                    return cartRepository.save(newCart);
                });

        return buildCartResponse(cart);
    }

    @Transactional
    public CartResponse updateCartItem(UpdateCartItemRequest request) {
        User user = getAuthenticatedUser();

        // Find the cart item
        CartItem cartItem = cartItemRepository.findById(request.getCartItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found with id: " + request.getCartItemId()));

        // Validate ownership
        if (!cartItem.getCart().getUser().getId().equals(user.getId())) {
            throw new UnauthorizedAccessException("You are not authorized to modify this cart item");
        }

        // Validate quantity
        if (request.getQuantity() <= 0) {
            throw new InvalidOperationException("Quantity must be greater than zero");
        }

        // Validate stock availability
        Product product = cartItem.getProduct();
        if (request.getQuantity() > product.getStock()) {
            throw new InsufficientStockException(
                    "Insufficient stock. Available: " + product.getStock()
                            + ", Requested: " + request.getQuantity());
        }

        cartItem.setQuantity(request.getQuantity());
        cartItemRepository.save(cartItem);

        return buildCartResponse(cartItem.getCart());
    }

    @Transactional
    public CartResponse removeCartItem(Long cartItemId) {
        User user = getAuthenticatedUser();

        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found with id: " + cartItemId));

        // Validate ownership
        if (!cartItem.getCart().getUser().getId().equals(user.getId())) {
            throw new UnauthorizedAccessException("You are not authorized to remove this cart item");
        }

        Cart cart = cartItem.getCart();
        cart.getItems().remove(cartItem);
        cartItemRepository.delete(cartItem);

        return buildCartResponse(cart);
    }

    @Transactional
    public CartResponse clearCart() {
        User user = getAuthenticatedUser();

        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));

        cart.getItems().clear();
        cartRepository.save(cart);

        return buildCartResponse(cart);
    }

    // --- Helper Methods ---

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (User) authentication.getPrincipal();
    }

    private CartResponse buildCartResponse(Cart cart) {
        List<CartItemResponse> itemResponses = cart.getItems().stream()
                .map(this::convertToCartItemResponse)
                .collect(Collectors.toList());

        BigDecimal totalAmount = itemResponses.stream()
                .map(CartItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalItems = itemResponses.stream()
                .mapToInt(CartItemResponse::getQuantity)
                .sum();

        return CartResponse.builder()
                .cartId(cart.getId())
                .items(itemResponses)
                .totalAmount(totalAmount)
                .totalItems(totalItems)
                .build();
    }

    private CartItemResponse convertToCartItemResponse(CartItem cartItem) {
        Product product = cartItem.getProduct();
        BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));

        return CartItemResponse.builder()
                .cartItemId(cartItem.getId())
                .productId(product.getId())
                .productName(product.getName())
                .imageUrl(product.getImageUrl())
                .price(product.getPrice())
                .quantity(cartItem.getQuantity())
                .subtotal(subtotal)
                .build();
    }
}
