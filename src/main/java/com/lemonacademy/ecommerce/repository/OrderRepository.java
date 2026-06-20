package com.lemonacademy.ecommerce.repository;

import com.lemonacademy.ecommerce.entity.Order;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserOrderByCreatedAtDesc(User user);
    List<Order> findAllByOrderByCreatedAtDesc();
    long countByStatus(OrderStatus status);
}
