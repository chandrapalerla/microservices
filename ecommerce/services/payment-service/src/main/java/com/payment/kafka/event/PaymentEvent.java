package com.payment.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published to "payment-events" topic.
 * Consumed by order-service to update order status.
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

    /** Populated on PAYMENT_FAILED */
    private String reason;

    private Instant timestamp;
}
