package com.payment.service;

import com.payment.dto.PaymentResponse;
import com.payment.entity.Payment;
import com.payment.entity.PaymentOutboxEvent;
import com.payment.enums.OutboxStatus;
import com.payment.enums.PaymentMethod;
import com.payment.enums.PaymentStatus;
import com.payment.exception.ResourceNotFoundException;
import com.payment.gateway.PaymentGateway;
import com.payment.gateway.dto.GatewayChargeResult;
import com.payment.kafka.PaymentEventPublisher;
import com.payment.kafka.event.OrderEvent;
import com.payment.observability.PaymentMetrics;
import com.payment.repository.PaymentOutboxRepository;
import com.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final List<PaymentGateway>    gateways;
    private final PaymentRepository       paymentRepository;
    private final PaymentOutboxRepository outboxRepository;
    private final PaymentEventPublisher   eventPublisher;
    private final PaymentMetrics          metrics;

    // ── Triggered by ORDER_CREATED Kafka event ──────────────────────────────────

    @Transactional
    public void processPayment(OrderEvent event) {
        // Idempotency: skip if already processed (Kafka at-least-once delivery)
        if (paymentRepository.existsByOrderId(event.getOrderId())) {
            log.info("Payment already processed for orderId={} — skipping duplicate", event.getOrderId());
            return;
        }

        PaymentMethod method = resolveMethod(event.getPaymentMethod());
        log.info("Processing payment for orderId={} method={} amount={} {}",
                event.getOrderId(), method, event.getTotalAmount(), "INR");

        Payment payment = Payment.builder()
                .orderId(event.getOrderId())
                .orderNumber(event.getOrderNumber())
                .userId(event.getUserId())
                .paymentMethod(method)
                .amount(event.getTotalAmount())
                .currency("INR")
                .status(PaymentStatus.PROCESSING)
                .retryCount(0)
                .build();
        payment = paymentRepository.saveAndFlush(payment);

        PaymentGateway gateway = gateways.stream()
                .filter(g -> g.supports(method))
                .findFirst()
                .orElse(null);

        if (gateway == null) {
            log.error("No gateway configured for method={}", method);
            fail(payment, "No payment gateway configured for method: " + method);
            return;
        }

        try {
            GatewayChargeResult result = gateway.charge(
                    event.getOrderId(),
                    event.getOrderNumber(),
                    event.getTotalAmount(),
                    "INR",
                    method);

            if (!result.isSuccess()) {
                fail(payment, result.getFailureReason());
                return;
            }

            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setGatewayProvider(result.getProvider());
            payment.setGatewayTxnId(result.getTransactionId());
            paymentRepository.save(payment);

            // Write outbox record — OutboxPoller will relay to Kafka
            saveOutbox(payment, "PAYMENT_COMPLETED", null);
            metrics.incrementCompleted(method.name());
            log.info("Payment COMPLETED orderId={} txn={}", event.getOrderId(), result.getTransactionId());

        } catch (Exception ex) {
            log.error("Payment FAILED for orderId={}: {}", event.getOrderId(), ex.getMessage());
            fail(payment, ex.getMessage());
        }
    }

    // ── Triggered by retry endpoint ──────────────────────────────────────────────

    @Transactional
    public PaymentResponse retryPayment(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", orderId));

        if (payment.getStatus() != PaymentStatus.FAILED) {
            throw new IllegalStateException("Payment retry only allowed for FAILED payments. Current: " + payment.getStatus());
        }

        payment.setStatus(PaymentStatus.PROCESSING);
        payment.setRetryCount(payment.getRetryCount() + 1);
        payment.setFailureReason(null);
        Payment saved = paymentRepository.saveAndFlush(payment);

        PaymentGateway gateway = gateways.stream()
                .filter(g -> g.supports(saved.getPaymentMethod()))
                .findFirst()
                .orElseThrow();
        payment = saved;

        try {
            GatewayChargeResult result = gateway.charge(
                    payment.getOrderId(),
                    payment.getOrderNumber(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getPaymentMethod());

            if (!result.isSuccess()) {
                fail(payment, result.getFailureReason());
                return toResponse(payment);
            }

            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setGatewayProvider(result.getProvider());
            payment.setGatewayTxnId(result.getTransactionId());
            paymentRepository.save(payment);
            saveOutbox(payment, "PAYMENT_COMPLETED", null);
            metrics.incrementCompleted(payment.getPaymentMethod().name());
            log.info("Payment retry COMPLETED orderId={} txn={}", orderId, result.getTransactionId());

        } catch (Exception ex) {
            fail(payment, ex.getMessage());
        }

        return toResponse(payment);
    }

    // ── Query operations ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PaymentResponse getByOrderId(Long orderId) {
        return toResponse(paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for orderId: " + orderId)));
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> getAll(Pageable pageable) {
        return paymentRepository.findAll(pageable).map(this::toResponse);
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    private void fail(Payment payment, String reason) {
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(truncate(reason, 500));
        paymentRepository.save(payment);
        saveOutbox(payment, "PAYMENT_FAILED", reason);
        metrics.incrementFailed(payment.getPaymentMethod().name());
    }

    private void saveOutbox(Payment payment, String eventType, String reason) {
        outboxRepository.save(PaymentOutboxEvent.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .eventType(eventType)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .build());
    }

    private PaymentMethod resolveMethod(String raw) {
        if (raw == null) return PaymentMethod.CASH_ON_DELIVERY;
        try {
            return PaymentMethod.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown payment method '{}', defaulting to CASH_ON_DELIVERY", raw);
            return PaymentMethod.CASH_ON_DELIVERY;
        }
    }

    private PaymentResponse toResponse(Payment p) {
        return PaymentResponse.builder()
                .id(p.getId())
                .orderId(p.getOrderId())
                .orderNumber(p.getOrderNumber())
                .userId(p.getUserId())
                .paymentMethod(p.getPaymentMethod())
                .gatewayProvider(p.getGatewayProvider())
                .gatewayTxnId(p.getGatewayTxnId())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .status(p.getStatus())
                .failureReason(p.getFailureReason())
                .retryCount(p.getRetryCount())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
