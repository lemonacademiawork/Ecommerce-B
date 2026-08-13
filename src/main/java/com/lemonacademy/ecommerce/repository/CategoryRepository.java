package com.lemonacademy.ecommerce.repository;

import java.util.UUID;
import java.util.List;

import com.lemonacademy.ecommerce.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {
    List<Category> findAllByActiveTrue();
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, UUID id);

    @Query("SELECT c FROM Category c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Category> searchCategories(@Param("query") String query);

    @Query("SELECT c FROM Category c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) AND c.active = true")
    List<Category> searchActiveCategories(@Param("query") String query);
}
