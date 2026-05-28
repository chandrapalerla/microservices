package com.product.dto.response;

import com.product.enums.ProductStatus;
import com.product.enums.StockStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Full product detail DTO — returned on single-item lookups (getById, getBySku).
 * Implements Serializable so Hazelcast can cache it with Java serialization.
 *
 * Computed fields (set by ProductMapper):
 *   discountPercent — derived from price vs originalPrice
 *   stockStatus     — derived from stockQuantity vs lowStockThreshold
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String sku;
    private String name;
    private String description;
    private String brand;

    private BigDecimal price;
    private BigDecimal originalPrice;

    /** Computed: rounded to 1 decimal place. Null when no discount applies. */
    private BigDecimal discountPercent;

    private Integer stockQuantity;
    private Integer reservedQuantity;
    private Integer lowStockThreshold;
    private ProductStatus status;

    /** Computed: IN_STOCK | LOW_STOCK | OUT_OF_STOCK */
    private StockStatus stockStatus;

    private String thumbnailUrl;
    private BigDecimal weight;

    /** Full category details (parentId + parentName included). */
    private CategoryResponse category;

    /** Exposed to clients for optimistic locking on update operations. */
    private Long version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
