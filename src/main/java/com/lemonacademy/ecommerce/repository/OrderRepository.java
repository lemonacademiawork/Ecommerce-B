package com.lemonacademy.ecommerce.repository;

import java.util.UUID;

import com.lemonacademy.ecommerce.entity.Order;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.entity.OrderStatus;
import com.lemonacademy.ecommerce.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByUserOrderByCreatedAtDesc(User user);
    Page<Order> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
    List<Order> findAllByOrderByCreatedAtDesc();
    long countByStatus(OrderStatus status);
    java.util.Optional<Order> findByAwbNumber(String awbNumber);
    java.util.Optional<Order> findByShipmentId(String shipmentId);
    java.util.Optional<Order> findByOrderNumber(String orderNumber);
    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<Order> findAllByPaymentStatus(PaymentStatus paymentStatus, Pageable pageable);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.paymentStatus = :paymentStatus")
    BigDecimal sumTotalAmountByPaymentStatus(@Param("paymentStatus") PaymentStatus paymentStatus);
}
