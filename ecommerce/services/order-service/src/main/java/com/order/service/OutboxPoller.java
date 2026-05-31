package com.order.service;

import com.order.entity.Order;
import com.order.entity.OutboxEvent;
import com.order.enums.OutboxStatus;
import com.order.kafka.OrderEventPublisher;
import com.order.repository.OrderRepository;
import com.order.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Polls the outbox_events table every 5 seconds and relays PENDING records to Kafka.
 *
 * Writing the outbox record in the same DB transaction as the order state change gives
 * at-least-once delivery — if Kafka is down the record stays PENDING and is retried here.
 * After 3 consecutive failures the record is marked FAILED for ops inspection.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {

    private static final int MAX_RETRIES = 3;

    private final OutboxEventRepository outboxEventRepository;
    private final OrderRepository       orderRepository;
    private final OrderEventPublisher   eventPublisher;

    @Scheduled(fixedDelay = 5_000)
    @Transactional
    public void poll() {
        List<OutboxEvent> pending = outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        if (pending.isEmpty()) return;

        log.debug("OutboxPoller processing {} pending event(s)", pending.size());

        for (OutboxEvent outboxEvent : pending) {
            try {
                Order order = orderRepository.findById(outboxEvent.getOrderId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Order " + outboxEvent.getOrderId() + " not found for outbox event " + outboxEvent.getId()));

                eventPublisher.publish(order, outboxEvent.getEventType(), outboxEvent.getReason());

                outboxEvent.setStatus(OutboxStatus.SENT);
                outboxEvent.setProcessedAt(Instant.now());
            } catch (Exception e) {
                int retries = outboxEvent.getRetryCount() + 1;
                outboxEvent.setRetryCount(retries);
                if (retries >= MAX_RETRIES) {
                    outboxEvent.setStatus(OutboxStatus.FAILED);
                    log.error("Outbox event {} permanently failed after {} retries: {}",
                            outboxEvent.getId(), retries, e.getMessage());
                } else {
                    log.warn("Outbox event {} failed (attempt {}): {}", outboxEvent.getId(), retries, e.getMessage());
                }
            }
        }
    }
}
