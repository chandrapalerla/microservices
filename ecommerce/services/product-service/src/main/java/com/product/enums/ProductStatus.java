package com.product.enums;

/**
 * Lifecycle status of a product in the catalogue.
 *
 *   ACTIVE       — visible and purchasable
 *   INACTIVE     — hidden from customers (admin use only)
 *   OUT_OF_STOCK — visible but cannot be purchased; set automatically when stockQuantity = 0
 *   DISCONTINUED — soft-deleted; hidden and never re-activated
 */
public enum ProductStatus {
    ACTIVE,
    INACTIVE,
    OUT_OF_STOCK,
    DISCONTINUED
}
