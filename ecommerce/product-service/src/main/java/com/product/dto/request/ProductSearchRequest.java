package com.product.dto.request;

import com.product.enums.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Query parameters for the dynamic product search endpoint.
 * All fields are optional; null values are ignored when building the JPA Specification.
 *
 * cacheKey() provides a stable Hazelcast cache key from all non-null fields.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSearchRequest {

    /** Case-insensitive partial match on product name. */
    private String name;

    /** Filter to products belonging to this category. */
    private Long categoryId;

    /** Case-insensitive partial match on brand name. */
    private String brand;

    private BigDecimal minPrice;
    private BigDecimal maxPrice;

    /** When true, only return products with stockQuantity > 0. */
    private Boolean inStockOnly;

    /** Defaults to ACTIVE in ProductSpecification when null (hides hidden/discontinued products). */
    private ProductStatus status;

    /**
     * Stable, deterministic string used as a Hazelcast cache key.
     * All fields are normalized to lower-case where applicable.
     */
    public String cacheKey() {
        return "search"
                + ":" + (name      != null ? name.toLowerCase().trim() : "_")
                + ":" + (categoryId != null ? categoryId : "_")
                + ":" + (brand     != null ? brand.toLowerCase().trim() : "_")
                + ":" + (minPrice  != null ? minPrice.toPlainString() : "_")
                + ":" + (maxPrice  != null ? maxPrice.toPlainString() : "_")
                + ":" + (inStockOnly != null ? inStockOnly : "_")
                + ":" + (status   != null ? status.name() : "ACTIVE");
    }
}
