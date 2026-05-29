package com.product.kafka;

import com.product.kafka.event.ProductEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Self-consumer for the product-events topic.
 *
 * groupId = "product-service-alerts" — isolated from external consumers so this
 * listener gets its own offset pointer and doesn't interfere with other subscribers.
 *
 * Current behaviour: logs STOCK_LOW and STOCK_OUT events as structured warnings so
 * they can be captured by log-based alerting (Loki/AlertManager or similar).
 *
 * Extension points:
 *   - Forward STOCK_LOW/STOCK_OUT to a notification queue (email, Slack, PagerDuty)
 *   - Trigger an automatic purchase-order workflow on STOCK_OUT
 *   - Update an analytics aggregate on PRODUCT_CREATED / STOCK_DEDUCTED
 */
@Slf4j
@Component
public class ProductEventListener {

    @KafkaListener(topics = "product-events", groupId = "product-service-alerts")
    public void handleProductEvent(ProductEvent event) {
        if (event == null || event.getEventType() == null) return;

        switch (event.getEventType()) {
            case ProductEventPublisher.STOCK_LOW ->
                log.warn("LOW_STOCK_ALERT productId={} sku={} remaining={} threshold={}",
                        event.getProductId(), event.getSku(),
                        event.getStockQuantity(), "see product record");

            case ProductEventPublisher.STOCK_OUT ->
                log.warn("OUT_OF_STOCK_ALERT productId={} sku={} — stock is now 0",
                        event.getProductId(), event.getSku());

            case ProductEventPublisher.PRODUCT_CREATED ->
                log.info("PRODUCT_CREATED productId={} sku={} name={}",
                        event.getProductId(), event.getSku(), event.getProductName());

            default ->
                log.debug("Received product event type={} productId={}",
                        event.getEventType(), event.getProductId());
        }
    }
}
