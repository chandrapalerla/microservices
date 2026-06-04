package com.order.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.entity.DeadLetterEvent;
import com.order.entity.Order;
import com.order.entity.OrderItem;
import com.order.kafka.event.OrderEvent;
import com.order.repository.DeadLetterEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Publishes OrderEvents to the "order-events" Kafka topic.
 *
 * Key = orderId — guarantees ordering within a single order's events.
 *
 * On publish failure the event is persisted to dead_letter_events so it is
 * never silently lost. Ops can inspect and replay DLQ records after broker recovery.
 * DLQ save runs in its own transaction (Spring Data's default) on the Kafka
 * callback thread — completely decoupled from the caller's DB transaction.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private static final String TOPIC = "order-events";

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
    private final DeadLetterEventRepository         deadLetterEventRepository;
    private final ObjectMapper                      objectMapper;

    public void publish(Order order, String eventType) {
        publish(order, eventType, null);
    }

    public void publish(Order order, String eventType, String reason) {
        OrderEvent event = OrderEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUserId())
                .userEmail(order.getUserEmail())
                .paymentMethod(order.getPaymentMethod() != null ? order.getPaymentMethod().name() : null)
                .items(mapItems(order.getItems()))
                .totalAmount(order.getTotalAmount())
                .currency("INR")
                .trackingNumber(order.getTrackingNumber())
                .reason(reason)
                .timestamp(Instant.now())
                .build();

        String key = order.getId().toString();
        CompletableFuture<SendResult<String, OrderEvent>> future = kafkaTemplate.send(TOPIC, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish {} event for order {}: {}", eventType, order.getId(), ex.getMessage());
                saveToDlq(event, ex.getMessage());
            } else {
                log.debug("Published {} event for order {} to partition {} offset {}",
                        eventType, order.getId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    private void saveToDlq(OrderEvent event, String errorMessage) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            DeadLetterEvent dlq = DeadLetterEvent.builder()
                    .eventId(event.getEventId())
                    .eventType(event.getEventType())
                    .orderId(event.getOrderId())
                    .payload(payload)
                    .errorMessage(truncate(errorMessage, 500))
                    .build();
            deadLetterEventRepository.save(dlq);
            log.warn("Saved failed event {} to DLQ", event.getEventId());
        } catch (Exception saveEx) {
            log.error("Could not save event {} to DLQ: {}", event.getEventId(), saveEx.getMessage());
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private List<OrderEvent.OrderEventItem> mapItems(List<OrderItem> items) {
        if (items == null) return List.of();
        return items.stream()
                .map(i -> OrderEvent.OrderEventItem.builder()
                        .productId(i.getProductId())
                        .productName(i.getProductName())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .build())
                .toList();
    }
}
