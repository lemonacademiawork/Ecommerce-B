package com.lemonacademy.ecommerce.repository;

import com.lemonacademy.ecommerce.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByActiveTrue();
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, Long id);
}
