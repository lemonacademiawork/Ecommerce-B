package com.lemonacademy.ecommerce.repository;

import java.util.UUID;

import com.lemonacademy.ecommerce.entity.Cart;
import com.lemonacademy.ecommerce.entity.CartItem;
import com.lemonacademy.ecommerce.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
    Optional<CartItem> findByCartAndProduct(Cart cart, Product product);
}
