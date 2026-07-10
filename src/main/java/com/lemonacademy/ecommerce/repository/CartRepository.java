package com.lemonacademy.ecommerce.repository;

import java.util.UUID;

import com.lemonacademy.ecommerce.entity.Cart;
import com.lemonacademy.ecommerce.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, UUID> {
    Optional<Cart> findByUser(User user);
}
