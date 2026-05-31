package com.user.kafka;

import java.time.Instant;

/**
 * Immutable event published to the 'user-events' Kafka topic on every write.
 *
 * role and status are String (not enum) so consuming services do not need
 * the same enum classes on their classpath to deserialise the event.
 *
 * eventId is a random UUID — consumers can use it for idempotency checks.
 */
public record UserEvent(
        String  eventId,
        String  eventType,
        Long    userId,
        String  email,
        String  name,
        String  role,
        String  status,
        Instant occurredAt
) {}
