package com.user.kafka;

import com.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);

    private final KafkaTemplate<String, UserEvent> kafkaTemplate;

    /**
     * Publishes a user lifecycle event to the 'user-events' topic.
     *
     * The message key is the userId string so all events for the same user land
     * on the same partition, preserving ordering for downstream consumers.
     *
     * Kafka failures are caught and logged but never rethrown — a broker outage
     * must not roll back the already-committed DB transaction.
     * For guaranteed delivery, replace this with the Outbox pattern.
     */
    public void publish(UserEventType type, User user) {
        UserEvent event = new UserEvent(
                UUID.randomUUID().toString(),
                type.name(),
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole()   != null ? user.getRole().name()   : null,
                user.getStatus() != null ? user.getStatus().name() : null,
                Instant.now()
        );
        try {
            kafkaTemplate.send(KafkaConfig.USER_EVENTS_TOPIC, String.valueOf(user.getId()), event);
            log.debug("Published {} for userId={}", type, user.getId());
        } catch (Exception e) {
            log.error("Failed to publish {} for userId={}: {}", type, user.getId(), e.getMessage());
        }
    }
}
