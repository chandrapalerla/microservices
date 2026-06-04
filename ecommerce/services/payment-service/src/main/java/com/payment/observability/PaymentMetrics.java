package com.payment.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PaymentMetrics {

    private final MeterRegistry registry;

    public PaymentMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void incrementCompleted(String method) {
        Counter.builder("payment.completed")
                .description("Successful payments")
                .tag("method", method)
                .register(registry)
                .increment();
    }

    public void incrementFailed(String method) {
        Counter.builder("payment.failed")
                .description("Failed payments")
                .tag("method", method)
                .register(registry)
                .increment();
    }

    public void incrementRefunded() {
        Counter.builder("payment.refunded")
                .description("Refunded payments")
                .register(registry)
                .increment();
    }
}
