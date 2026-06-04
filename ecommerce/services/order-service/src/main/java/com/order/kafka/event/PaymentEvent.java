package com.order.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Consumed from "payment-events" topic.
 * Drives order status transitions: PENDING → CONFIRMED (on PAYMENT_COMPLETED)
 * or PENDING → PAYMENT_FAILED (on PAYMENT_FAILED).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEvent {

    private String eventId;

    /**
     * PAYMENT_COMPLETED | PAYMENT_FAILED | PAYMENT_REFUNDED
     */
    private String eventType;

    private Long orderId;
    private String orderNumber;
    private Long paymentId;
    private String gatewayTransactionId;
    private String gatewayProvider;
    private BigDecimal amount;
    private String currency;
    private String reason;
    private Instant timestamp;
}
