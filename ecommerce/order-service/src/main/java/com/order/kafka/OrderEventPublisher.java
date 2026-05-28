package com.order.kafka;

import com.order.entity.Order;
import com.order.entity.OrderItem;
import com.order.kafka.event.OrderEvent;
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
 * Key   = orderId.toString() — guarantees that all events for the same order
 *         land in the same partition, preserving chronological ordering.
 *
 * Failures are logged but NOT re-thrown.  The order state has already been
 * persisted to the database when this is called; a Kafka failure should not
 * roll back the DB transaction.  Consumers can replay from the DB if needed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private static final String TOPIC = "order-events";

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

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
                .items(mapItems(order.getItems()))
                .totalAmount(order.getTotalAmount())
                .trackingNumber(order.getTrackingNumber())
                .reason(reason)
                .timestamp(Instant.now())
                .build();

        String key = order.getId().toString();
        CompletableFuture<SendResult<String, OrderEvent>> future = kafkaTemplate.send(TOPIC, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish {} event for order {}: {}",
                        eventType, order.getId(), ex.getMessage());
            } else {
                log.debug("Published {} event for order {} to partition {} offset {}",
                        eventType, order.getId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
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
