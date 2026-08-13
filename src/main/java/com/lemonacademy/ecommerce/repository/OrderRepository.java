package com.lemonacademy.ecommerce.repository;

import java.util.UUID;
import java.time.LocalDateTime;

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
    long countByPaymentStatus(PaymentStatus paymentStatus);
    java.util.Optional<Order> findByAwbNumber(String awbNumber);
    java.util.Optional<Order> findByShipmentId(String shipmentId);
    java.util.Optional<Order> findByOrderNumber(String orderNumber);
    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<Order> findAllByPaymentStatus(PaymentStatus paymentStatus, Pageable pageable);
    List<Order> findAllByCreatedAtGreaterThanEqual(LocalDateTime startDateTime);

    @Query("SELECT o FROM Order o JOIN o.user u WHERE " +
           "LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(CAST(o.id AS string)) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.phone) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Order> searchOrders(@Param("query") String query, Pageable pageable);

    @Query("SELECT o FROM Order o JOIN o.user u WHERE " +
           "(:paymentStatus IS NULL OR o.paymentStatus = :paymentStatus) AND " +
           "(:status IS NULL OR o.status = :status) AND " +
           "(:query IS NULL OR :query = '' OR " +
           "LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(CAST(o.id AS string)) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.phone) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Order> searchAndFilterOrders(
            @Param("query") String query,
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("status") OrderStatus status,
            Pageable pageable);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.paymentStatus = :paymentStatus")
    BigDecimal sumTotalAmountByPaymentStatus(@Param("paymentStatus") PaymentStatus paymentStatus);

    @Query("SELECT COUNT(oi) > 0 FROM OrderItem oi WHERE oi.order.user.id = :userId AND oi.product.id = :productId AND oi.order.status = com.lemonacademy.ecommerce.entity.OrderStatus.DELIVERED")
    boolean hasUserPurchasedProductAndDelivered(@Param("userId") UUID userId, @Param("productId") UUID productId);
}
