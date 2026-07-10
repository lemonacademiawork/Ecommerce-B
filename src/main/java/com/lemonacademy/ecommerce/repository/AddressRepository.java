package com.lemonacademy.ecommerce.repository;

import java.util.UUID;

import com.lemonacademy.ecommerce.entity.Address;
import com.lemonacademy.ecommerce.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AddressRepository extends JpaRepository<Address, UUID> {
    List<Address> findByUserOrderByCreatedAtDesc(User user);
    List<Address> findByUser(User user);
    Optional<Address> findByIdAndUser(UUID id, User user);
    Optional<Address> findByUserAndIsDefaultTrue(User user);
}
