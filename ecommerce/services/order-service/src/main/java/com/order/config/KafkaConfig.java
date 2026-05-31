package com.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the Kafka topics this service owns.
 *
 * order-events: 3 partitions so concurrent order processing can be parallelised.
 *   Key   = orderId → events for the same order go to the same partition (ordering guarantee).
 *   Value = OrderEvent (JSON)
 *
 * If the topic already exists in the broker, Spring Kafka skips creation silently.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name("order-events")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
