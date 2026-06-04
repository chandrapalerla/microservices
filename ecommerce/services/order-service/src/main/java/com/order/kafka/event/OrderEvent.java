package com.order.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Kafka event published on every order status transition.
 *
 * Topic : order-events  (3 partitions)
 * Key   : orderId (String)  — guarantees ordering within a single order's events
 *
 * Consumers (e.g. notification-service) subscribe to this topic to send emails,
 * update inventory, or trigger payment flows.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderEvent {

    /** UUID — unique event identifier (idempotency key for consumers). */
    private String eventId;

    /**
     * Type of event:
     *   ORDER_CREATED | ORDER_CONFIRMED | ORDER_PAYMENT_FAILED |
     *   ORDER_PROCESSING | ORDER_SHIPPED | ORDER_OUT_FOR_DELIVERY |
     *   ORDER_DELIVERED | ORDER_RETURN_REQUESTED | ORDER_RETURNED |
     *   ORDER_REFUNDED | ORDER_CANCELLED
     */
    private String eventType;

    private Long orderId;
    private String orderNumber;
    private Long userId;
    private String userEmail;

    /** Required by payment-service to route to the correct gateway. */
    private String paymentMethod;

    private List<OrderEventItem> items;

    private BigDecimal totalAmount;
    private String currency;
    private String trackingNumber;
    private String reason;

    private Instant timestamp;

    // ── Embedded item snapshot ────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderEventItem {
        private Long productId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
    }
}
