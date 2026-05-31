package com.product.enums;

/**
 * Computed stock availability label — derived from stockQuantity vs lowStockThreshold.
 * Not persisted; computed by ProductMapper on every response.
 */
public enum StockStatus {
    IN_STOCK,
    LOW_STOCK,
    OUT_OF_STOCK
}
