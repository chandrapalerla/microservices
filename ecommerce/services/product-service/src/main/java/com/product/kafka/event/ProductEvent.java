package com.product.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Kafka event published on every product or stock change.
 *
 * Topic  : product-events  (3 partitions)
 * Key    : productId.toString() — events for the same product land in the same partition
 *
 * Event types:
 *   PRODUCT_CREATED       — new product added to the catalogue
 *   PRODUCT_UPDATED       — name / price / description changed
 *   PRODUCT_STATUS_CHANGED — ACTIVE ↔ INACTIVE ↔ DISCONTINUED
 *   STOCK_DEDUCTED        — stock reduced by an order (order-service calls deduct-stock)
 *   STOCK_RESTORED        — stock returned due to order cancellation
 *   STOCK_UPDATED         — admin set absolute stock value
 *   STOCK_LOW             — fired additionally when stock drops below lowStockThreshold
 *   STOCK_OUT             — fired additionally when stockQuantity reaches 0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductEvent {

    /** UUID — unique event ID for consumer idempotency. */
    private String eventId;

    private String eventType;

    private Long productId;
    private String productName;
    private String sku;
    private BigDecimal price;

    /** Stock quantity AFTER this event was applied. */
    private Integer stockQuantity;

    /**
     * Change in stock quantity for this event.
     * Positive = stock added (restore/set); negative = stock removed (deduct).
     * Null for non-stock events.
     */
    private Integer stockDelta;

    private String status;
    private Long categoryId;

    private Instant timestamp;
}
