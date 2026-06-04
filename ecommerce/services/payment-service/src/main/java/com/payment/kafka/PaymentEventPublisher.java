package com.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.entity.Payment;
import com.payment.entity.PaymentOutboxEvent;
import com.payment.enums.OutboxStatus;
import com.payment.kafka.event.PaymentEvent;
import com.payment.repository.PaymentOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {

    private static final String TOPIC = "payment-events";

    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;
    private final PaymentOutboxRepository             outboxRepository;
    private final ObjectMapper                        objectMapper;

    public void publishCompleted(Payment payment) {
        publish(payment, "PAYMENT_COMPLETED", null);
    }

    public void publishFailed(Payment payment, String reason) {
        publish(payment, "PAYMENT_FAILED", reason);
    }

    public void publishRefunded(Payment payment) {
        publish(payment, "PAYMENT_REFUNDED", null);
    }

    private void publish(Payment payment, String eventType, String reason) {
        PaymentEvent event = PaymentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .orderId(payment.getOrderId())
                .orderNumber(payment.getOrderNumber())
                .paymentId(payment.getId())
                .gatewayTransactionId(payment.getGatewayTxnId())
                .gatewayProvider(payment.getGatewayProvider() != null
                        ? payment.getGatewayProvider().name() : null)
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .reason(reason)
                .timestamp(Instant.now())
                .build();

        String key = payment.getOrderId().toString();
        CompletableFuture<SendResult<String, PaymentEvent>> future =
                kafkaTemplate.send(TOPIC, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish {} event for orderId={}: {}",
                        eventType, payment.getOrderId(), ex.getMessage());
                saveToDlq(payment, event, ex.getMessage());
            } else {
                log.debug("Published {} event for orderId={} to partition {} offset {}",
                        eventType, payment.getOrderId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    private void saveToDlq(Payment payment, PaymentEvent event, String errorMessage) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxRepository.save(PaymentOutboxEvent.builder()
                    .paymentId(payment.getId())
                    .orderId(payment.getOrderId())
                    .eventType(event.getEventType())
                    .payload(payload)
                    .status(OutboxStatus.FAILED)
                    .retryCount(0)
                    .build());
            log.warn("Saved failed payment event to outbox for orderId={}", payment.getOrderId());
        } catch (Exception saveEx) {
            log.error("Could not save failed payment event to outbox: {}", saveEx.getMessage());
        }
    }
}
