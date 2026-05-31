package com.apigateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Resilience4jConfig {

    private static final Logger log = LoggerFactory.getLogger(Resilience4jConfig.class);

    /**
     * Attaches event listeners to every circuit breaker instance as it is created
     * by Resilience4j's registry.  Logs state transitions at WARN so they appear
     * in Logstash / Kibana without requiring a dedicated alert rule.
     *
     * State machine: CLOSED → OPEN → HALF_OPEN → CLOSED (or back to OPEN)
     */
    @Bean
    public RegistryEventConsumer<CircuitBreaker> circuitBreakerEventConsumer() {
        return new RegistryEventConsumer<>() {

            @Override
            public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> event) {
                CircuitBreaker cb = event.getAddedEntry();
                cb.getEventPublisher()
                        .onStateTransition(e -> log.warn(
                                "CircuitBreaker [{}] state transition: {} → {}",
                                cb.getName(),
                                e.getStateTransition().getFromState(),
                                e.getStateTransition().getToState()))
                        .onCallNotPermitted(e -> log.warn(
                                "CircuitBreaker [{}] is OPEN — request rejected without forwarding",
                                cb.getName()))
                        .onError(e -> log.debug(
                                "CircuitBreaker [{}] recorded failure ({} ms): {}",
                                cb.getName(),
                                e.getElapsedDuration().toMillis(),
                                e.getThrowable().getMessage()))
                        .onSlowCallRateExceeded(e -> log.warn(
                                "CircuitBreaker [{}] slow-call threshold exceeded: {}% slow calls",
                                cb.getName(),
                                e.getSlowCallRate()))
                        .onFailureRateExceeded(e -> log.warn(
                                "CircuitBreaker [{}] failure threshold exceeded: {}% failure rate",
                                cb.getName(),
                                e.getFailureRate()));
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> event) {}

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> event) {}
        };
    }
}
