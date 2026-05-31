package com.user.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String USER_EVENTS_TOPIC = "user-events";

    /**
     * Declares the topic idempotently — Kafka Admin creates it on startup if it
     * does not exist yet, and leaves it unchanged if it already does.
     *
     * 3 partitions: allows 3 parallel consumers (e.g. order-service replicas)
     * without re-ordering per-user events (partition key = userId string).
     */
    @Bean
    public NewTopic userEventsTopic() {
        return TopicBuilder.name(USER_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
