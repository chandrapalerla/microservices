package com.product.specification;

import com.product.dto.request.ProductSearchRequest;
import com.product.entity.Product;
import com.product.enums.ProductStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA Criteria API specification for dynamic product search.
 *
 * All filter fields are optional — null values produce no predicate.
 * When status is not specified, defaults to ACTIVE (hides hidden/discontinued products).
 *
 * Usage:
 *   productRepository.findAll(ProductSpecification.withFilters(filter), pageable)
 */
public final class ProductSpecification {

    private ProductSpecification() {}

    public static Specification<Product> withFilters(ProductSearchRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // ── Name: case-insensitive partial match ──────────────────────────
            if (filter.getName() != null && !filter.getName().isBlank()) {
                predicates.add(cb.like(
                        cb.lower(root.get("name")),
                        "%" + filter.getName().toLowerCase().trim() + "%"));
            }

            // ── Category ID: exact match ───────────────────────────────────────
            if (filter.getCategoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), filter.getCategoryId()));
            }

            // ── Brand: case-insensitive partial match ─────────────────────────
            if (filter.getBrand() != null && !filter.getBrand().isBlank()) {
                predicates.add(cb.like(
                        cb.lower(root.get("brand")),
                        "%" + filter.getBrand().toLowerCase().trim() + "%"));
            }

            // ── Price range ───────────────────────────────────────────────────
            if (filter.getMinPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), filter.getMinPrice()));
            }
            if (filter.getMaxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), filter.getMaxPrice()));
            }

            // ── In-stock only ─────────────────────────────────────────────────
            if (Boolean.TRUE.equals(filter.getInStockOnly())) {
                predicates.add(cb.greaterThan(root.get("stockQuantity"), 0));
            }

            // ── Status (default: ACTIVE for users; ADMIN can override) ────────
            ProductStatus effectiveStatus =
                    (filter.getStatus() != null) ? filter.getStatus() : ProductStatus.ACTIVE;
            predicates.add(cb.equal(root.get("status"), effectiveStatus));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
