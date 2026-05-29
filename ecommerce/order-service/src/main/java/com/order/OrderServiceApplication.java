package com.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Order Service — manages the full order lifecycle for the e-commerce platform.
 *
 * Port       : 2029
 * Database   : MySQL order_db  (schema managed by Flyway migrations in db/migration/)
 * Cache      : Hazelcast 5.7.0 (hazelcast.xml)
 * Security   : Keycloak JWT (OAuth2 resource server) + X-Gateway-Secret filter
 * Messaging  : Kafka producer (outbox pattern) + consumer (PAYMENT_FAILED handler)
 * Feign      : user-service (:2026), product-service (:2028)
 * Scheduling : OutboxPoller (5s), StockCompensationScheduler (30s)
 *
 * Startup order: serviceregistry → apigateway → order-service (and user-service)
 */
@SpringBootApplication
@EnableFeignClients
@EnableCaching
@EnableScheduling
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
