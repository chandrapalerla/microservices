package com.product.kafka;

import com.product.entity.Product;
import com.product.kafka.event.ProductEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes ProductEvent messages to the "product-events" Kafka topic.
 *
 * Partition key = productId.toString() — all events for the same product
 * are delivered to the same partition, preserving event ordering for consumers.
 *
 * Kafka failures are logged but NOT re-thrown; the database state is already
 * persisted and the product operation has succeeded.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventPublisher {

    private static final String TOPIC = "product-events";

    private final KafkaTemplate<String, ProductEvent> kafkaTemplate;

    // ── Event type constants ──────────────────────────────────────────────────

    public static final String PRODUCT_CREATED        = "PRODUCT_CREATED";
    public static final String PRODUCT_UPDATED        = "PRODUCT_UPDATED";
    public static final String PRODUCT_STATUS_CHANGED = "PRODUCT_STATUS_CHANGED";
    public static final String STOCK_DEDUCTED         = "STOCK_DEDUCTED";
    public static final String STOCK_RESTORED         = "STOCK_RESTORED";
    public static final String STOCK_UPDATED          = "STOCK_UPDATED";
    public static final String STOCK_LOW              = "STOCK_LOW";
    public static final String STOCK_OUT              = "STOCK_OUT";

    // ── Public publish methods ────────────────────────────────────────────────

    public void publishProductCreated(Product product) {
        publish(buildEvent(product, PRODUCT_CREATED, null));
    }

    public void publishProductUpdated(Product product) {
        publish(buildEvent(product, PRODUCT_UPDATED, null));
    }

    public void publishStatusChanged(Product product) {
        publish(buildEvent(product, PRODUCT_STATUS_CHANGED, null));
    }

    /**
     * Publishes STOCK_DEDUCTED event (delta is negative).
     * Also publishes STOCK_LOW or STOCK_OUT if applicable.
     */
    public void publishStockDeducted(Product product, int deductedQty) {
        publish(buildEvent(product, STOCK_DEDUCTED, -deductedQty));
        checkAndPublishLowOrOut(product);
    }

    /**
     * Publishes STOCK_RESTORED event (delta is positive).
     */
    public void publishStockRestored(Product product, int restoredQty) {
        publish(buildEvent(product, STOCK_RESTORED, restoredQty));
    }

    /**
     * Publishes STOCK_UPDATED event when admin sets absolute stock value.
     * Also publishes STOCK_LOW or STOCK_OUT if applicable.
     */
    public void publishStockUpdated(Product product, int delta) {
        publish(buildEvent(product, STOCK_UPDATED, delta));
        checkAndPublishLowOrOut(product);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * If stock is 0 → publish STOCK_OUT.
     * Else if stock < lowStockThreshold → publish STOCK_LOW.
     */
    private void checkAndPublishLowOrOut(Product product) {
        int qty = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
        int threshold = product.getLowStockThreshold() != null ? product.getLowStockThreshold() : 10;

        if (qty == 0) {
            publish(buildEvent(product, STOCK_OUT, null));
        } else if (qty < threshold) {
            publish(buildEvent(product, STOCK_LOW, null));
        }
    }

    private ProductEvent buildEvent(Product product, String eventType, Integer stockDelta) {
        return ProductEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .productId(product.getId())
                .productName(product.getName())
                .sku(product.getSku())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .stockDelta(stockDelta)
                .status(product.getStatus() != null ? product.getStatus().name() : null)
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .timestamp(Instant.now())
                .build();
    }

    private void publish(ProductEvent event) {
        String key = event.getProductId() != null ? event.getProductId().toString() : "unknown";
        try {
            kafkaTemplate.send(TOPIC, key, event);
            log.debug("Published {} event for product {} (key={})", event.getEventType(), event.getProductId(), key);
        } catch (Exception ex) {
            log.error("Failed to publish {} event for product {}: {}",
                    event.getEventType(), event.getProductId(), ex.getMessage(), ex);
        }
    }
}
