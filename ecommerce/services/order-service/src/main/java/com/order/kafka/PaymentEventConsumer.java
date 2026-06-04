package com.order.kafka;

import com.order.dto.request.PaymentConfirmRequest;
import com.order.kafka.event.PaymentEvent;
import com.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumes payment events from payment-service and drives order status transitions.
 *
 * PAYMENT_COMPLETED → order moves to CONFIRMED (paymentStatus = PAID)
 * PAYMENT_FAILED    → order moves to PAYMENT_FAILED, stock is restored
 *
 * Replaces the old OrderEventListener which was a placeholder for self-published
 * PAYMENT_FAILED events on the order-events topic.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final OrderService orderService;

    @KafkaListener(
            topics = "payment-events",
            groupId = "order-service-payment",
            containerFactory = "paymentEventListenerFactory"
    )
    public void onPaymentEvent(
            PaymentEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received payment event: type={} orderId={} partition={} offset={}",
                event.getEventType(), event.getOrderId(), partition, offset);

        switch (event.getEventType()) {
            case "PAYMENT_COMPLETED" -> {
                PaymentConfirmRequest req = new PaymentConfirmRequest();
                req.setPaymentReference(event.getGatewayTransactionId());
                req.setNotes("Payment captured via " + event.getGatewayProvider());
                orderService.confirmPayment(event.getOrderId(), req, "payment-service");
            }
            case "PAYMENT_FAILED" -> orderService.markPaymentFailed(event.getOrderId(), event.getReason());
            default -> log.debug("Ignoring payment event type: {}", event.getEventType());
        }
    }
}
