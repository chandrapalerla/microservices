package com.product.mapper;

import com.product.dto.response.ProductResponse;
import com.product.dto.response.ProductSummaryResponse;
import com.product.entity.Product;
import com.product.enums.StockStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * MapStruct compile-time mapper: Product entity ↔ ProductResponse / ProductSummaryResponse.
 *
 * Computed fields:
 *   discountPercent — ((originalPrice - price) / originalPrice × 100) rounded to 1dp
 *                     Returns null when originalPrice is null or ≤ price.
 *   stockStatus     — IN_STOCK / LOW_STOCK / OUT_OF_STOCK derived from quantities
 *
 * uses = CategoryMapper.class tells MapStruct to delegate Category → CategoryResponse
 * conversions for the nested `category` field in ProductResponse.
 */
@Mapper(componentModel = "spring", uses = {CategoryMapper.class})
public interface ProductMapper {

    @Mapping(target = "discountPercent", expression = "java(computeDiscountPercent(product))")
    @Mapping(target = "stockStatus",     expression = "java(computeStockStatus(product))")
    ProductResponse toResponse(Product product);

    @Mapping(target = "discountPercent", expression = "java(computeDiscountPercent(product))")
    @Mapping(target = "stockStatus",     expression = "java(computeStockStatus(product))")
    @Mapping(target = "categoryName",    source = "category.name")
    ProductSummaryResponse toSummary(Product product);

    // ── Computed helpers (called via expression = "java(...)") ────────────────

    default BigDecimal computeDiscountPercent(Product product) {
        if (product.getOriginalPrice() == null) return null;
        if (product.getOriginalPrice().compareTo(product.getPrice()) <= 0) return null;
        BigDecimal diff = product.getOriginalPrice().subtract(product.getPrice());
        return diff.divide(product.getOriginalPrice(), 4, RoundingMode.HALF_UP)
                   .multiply(new BigDecimal("100"))
                   .setScale(1, RoundingMode.HALF_UP);
    }

    default StockStatus computeStockStatus(Product product) {
        int qty = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
        int threshold = product.getLowStockThreshold() != null ? product.getLowStockThreshold() : 10;
        if (qty == 0)          return StockStatus.OUT_OF_STOCK;
        if (qty < threshold)   return StockStatus.LOW_STOCK;
        return StockStatus.IN_STOCK;
    }
}
