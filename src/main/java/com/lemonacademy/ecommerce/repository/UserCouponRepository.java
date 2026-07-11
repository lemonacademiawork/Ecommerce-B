package com.lemonacademy.ecommerce.repository;

import com.lemonacademy.ecommerce.entity.Coupon;
import com.lemonacademy.ecommerce.entity.User;
import com.lemonacademy.ecommerce.entity.UserCoupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserCouponRepository extends JpaRepository<UserCoupon, UUID> {
    Optional<UserCoupon> findByUserAndCoupon(User user, Coupon coupon);
    boolean existsByUserAndCoupon(User user, Coupon coupon);
}
