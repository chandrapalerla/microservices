package com.payment.scheduler;

import com.payment.entity.Payment;
import com.payment.entity.PaymentOutboxEvent;
import com.payment.enums.OutboxStatus;
import com.payment.kafka.PaymentEventPublisher;
import com.payment.repository.PaymentOutboxRepository;
import com.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Polls the payment_outbox table every 5 seconds and relays PENDING records to Kafka.
 *
 * Same at-least-once pattern as order-service OutboxPoller:
 * the outbox record is written in the same DB transaction as the payment state change,
 * so Kafka delivery failures never lose events.
 *
 * After MAX_RETRIES consecutive failures the record is marked FAILED for ops inspection.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentOutboxPoller {

    private static final int MAX_RETRIES = 3;

    private final PaymentOutboxRepository outboxRepository;
    private final PaymentRepository       paymentRepository;
    private final PaymentEventPublisher   eventPublisher;

    @Scheduled(fixedDelay = 5_000)
    @Transactional
    public void poll() {
        List<PaymentOutboxEvent> pending =
                outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        if (pending.isEmpty()) return;

        log.debug("PaymentOutboxPoller processing {} pending event(s)", pending.size());

        for (PaymentOutboxEvent outbox : pending) {
            try {
                Payment payment = paymentRepository.findById(outbox.getPaymentId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Payment " + outbox.getPaymentId() + " not found for outbox event " + outbox.getId()));

                switch (outbox.getEventType()) {
                    case "PAYMENT_COMPLETED" -> eventPublisher.publishCompleted(payment);
                    case "PAYMENT_FAILED"    -> eventPublisher.publishFailed(payment, payment.getFailureReason());
                    case "PAYMENT_REFUNDED"  -> eventPublisher.publishRefunded(payment);
                    default -> log.warn("Unknown outbox event type: {}", outbox.getEventType());
                }

                outbox.setStatus(OutboxStatus.SENT);
                outbox.setProcessedAt(Instant.now());

            } catch (Exception e) {
                int retries = outbox.getRetryCount() + 1;
                outbox.setRetryCount(retries);
                if (retries >= MAX_RETRIES) {
                    outbox.setStatus(OutboxStatus.FAILED);
                    log.error("Payment outbox event {} permanently failed after {} retries: {}",
                            outbox.getId(), retries, e.getMessage());
                } else {
                    log.warn("Payment outbox event {} failed (attempt {}): {}",
                            outbox.getId(), retries, e.getMessage());
                }
            }
        }
    }
}
