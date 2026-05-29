package com.order.kafka;

import com.order.entity.Order;
import com.order.entity.OutboxEvent;
import com.order.enums.OrderStatus;
import com.order.enums.OutboxStatus;
import com.order.kafka.event.OrderEvent;
import com.order.repository.OrderRepository;
import com.order.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-consumer for the order-events topic.
 *
 * Handles PAYMENT_FAILED events published by an upstream payment service.
 * Transitions the order from PENDING → PAYMENT_FAILED and persists an outbox
 * entry so the status change is also broadcast downstream.
 *
 * groupId = "order-service-internal" ensures this consumer group is isolated
 * from external consumers (notification-service, etc.) on the same topic.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener {

    private final OrderRepository       orderRepository;
    private final OutboxEventRepository outboxEventRepository;

    @KafkaListener(topics = "order-events", groupId = "order-service-internal")
    @Transactional
    public void handleOrderEvent(OrderEvent event) {
        if (!"PAYMENT_FAILED".equals(event.getEventType())) {
            return;
        }

        log.info("Received PAYMENT_FAILED for orderId={}", event.getOrderId());

        Order order = orderRepository.findById(event.getOrderId()).orElse(null);
        if (order == null) {
            log.warn("PAYMENT_FAILED event received for unknown orderId={}", event.getOrderId());
            return;
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            log.info("Order {} is already in status {}; skipping PAYMENT_FAILED transition",
                    event.getOrderId(), order.getStatus());
            return;
        }

        order.setStatus(OrderStatus.PAYMENT_FAILED);
        orderRepository.save(order);

        outboxEventRepository.save(OutboxEvent.builder()
                .orderId(order.getId())
                .eventType("PAYMENT_FAILED")
                .reason("Payment failed — order automatically transitioned")
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .build());

        log.info("Order {} transitioned to PAYMENT_FAILED", order.getId());
    }
}
