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

/**
 * Lightweight product DTO for paginated lists and search results.
 * Omits description and full category details to reduce response size.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSummaryResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String sku;
    private String name;
    private String brand;
    private BigDecimal price;
    private BigDecimal originalPrice;

    /** Computed: ((originalPrice - price) / originalPrice * 100), rounded to 1dp. Null if no discount. */
    private BigDecimal discountPercent;

    private Integer stockQuantity;
    private ProductStatus status;

    /** Computed: IN_STOCK / LOW_STOCK / OUT_OF_STOCK based on stockQuantity vs lowStockThreshold. */
    private StockStatus stockStatus;

    private String thumbnailUrl;
    private String categoryName;
}
