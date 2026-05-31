package com.product.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic configuration for product-service.
 *
 * Topic: product-events
 *   partitions : 3  — events for the same product land in the same partition
 *                     (keyed by productId.toString())
 *   replicas   : 1  — single-broker development setup; increase for production
 *
 * Spring Kafka auto-creates the topic on startup if it does not exist.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic productEventsTopic() {
        return TopicBuilder.name("product-events")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
