package com.lemonacademy.ecommerce.repository;

import java.util.UUID;

import com.lemonacademy.ecommerce.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminRepository extends JpaRepository<Admin, UUID> {
    Optional<Admin> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}
