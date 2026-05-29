package com.product.repository;

import com.product.entity.Product;
import com.product.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Extends JpaSpecificationExecutor to support dynamic Criteria API search
 * via ProductSpecification.withFilters().
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long>,
                                           JpaSpecificationExecutor<Product> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, Long id);

    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);

    Page<Product> findByCategoryIdAndStatus(Long categoryId, ProductStatus status, Pageable pageable);

    /**
     * Low-stock query: ACTIVE products where current stock is below their individual threshold.
     * Each product may have a different lowStockThreshold, so we compare the two columns.
     */
    @Query("SELECT p FROM Product p WHERE p.stockQuantity < p.lowStockThreshold AND p.status = :status")
    Page<Product> findLowStock(@Param("status") ProductStatus status, Pageable pageable);

    boolean existsByCategoryId(Long categoryId);

    /**
     * Full-text search across name, description, and brand using MySQL BOOLEAN MODE.
     * Requires V4 Flyway migration (FULLTEXT INDEX ft_product_search).
     * Example query: "laptop +gaming -refurbished"
     */
    @Query(value = "SELECT * FROM products WHERE MATCH(name, description, brand) AGAINST (?1 IN BOOLEAN MODE)",
           nativeQuery = true)
    List<Product> fullTextSearch(String query);
}
