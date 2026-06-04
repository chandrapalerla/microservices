package com.payment.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Consumed from "order-events" topic.
 * Payment-service only acts on ORDER_CREATED events.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderEvent {

    private String eventId;
    private String eventType;
    private Long orderId;
    private String orderNumber;
    private Long userId;
    private String userEmail;
    private String paymentMethod;
    private BigDecimal totalAmount;
    private String currency;
    private List<OrderEventItem> items;
    private String trackingNumber;
    private String reason;
    private Instant timestamp;

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
