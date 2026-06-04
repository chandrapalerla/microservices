package com.payment.kafka;

import com.payment.kafka.event.OrderEvent;
import com.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumes ORDER_CREATED events from the order-events topic.
 * Only ORDER_CREATED events trigger payment processing — all other event types are ignored.
 *
 * Idempotency is enforced in PaymentService.processPayment() via existsByOrderId().
 * At-least-once delivery from Kafka means duplicates are possible but safely skipped.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final PaymentService paymentService;

    @KafkaListener(
            topics = "order-events",
            groupId = "payment-service",
            containerFactory = "orderEventListenerFactory"
    )
    public void onOrderEvent(
            OrderEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        if (!"ORDER_CREATED".equals(event.getEventType())) {
            return;
        }

        log.info("Received ORDER_CREATED orderId={} orderNumber={} method={} partition={} offset={}",
                event.getOrderId(), event.getOrderNumber(), event.getPaymentMethod(), partition, offset);

        paymentService.processPayment(event);
    }
}
