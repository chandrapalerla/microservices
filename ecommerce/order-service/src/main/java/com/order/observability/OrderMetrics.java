package com.order.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OrderMetrics {

    private final Counter           orderCreated;
    private final Counter           orderCancelled;
    private final DistributionSummary orderAmount;
    private final MeterRegistry     registry;

    public OrderMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.orderCreated = Counter.builder("order.created")
                .description("Total orders placed")
                .register(registry);
        this.orderCancelled = Counter.builder("order.cancelled")
                .description("Total orders cancelled")
                .register(registry);
        this.orderAmount = DistributionSummary.builder("order.total.amount")
                .description("Distribution of order totals in INR")
                .baseUnit("INR")
                .register(registry);
    }

    public void incrementCreated()                 { orderCreated.increment(); }
    public void incrementCancelled()               { orderCancelled.increment(); }
    public void recordAmount(double amount)        { orderAmount.record(amount); }

    /** Records every status transition tagged with from/to so Grafana can filter by either. */
    public void recordTransition(String from, String to) {
        Counter.builder("order.status.transition")
                .description("Order state machine transitions")
                .tag("from", from != null ? from : "NONE")
                .tag("to", to)
                .register(registry)
                .increment();
    }
}
