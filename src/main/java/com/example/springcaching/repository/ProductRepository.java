package com.example.springcaching.repository;

import com.example.springcaching.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByCategoryId(Long categoryId);

    @Query("SELECT p FROM Product p JOIN FETCH p.category WHERE p.id = :id")
    java.util.Optional<Product> findByIdWithCategory(@Param("id") Long id);

    List<Product> findByNameContainingIgnoreCase(String name);

    boolean existsByNameAndCategoryId(String name, Long categoryId);
}
