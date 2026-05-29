package com.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Product Service — ecommerce product catalogue microservice.
 *
 * Port         : 2028
 * Eureka       : registers as "product-service"
 * Database     : MySQL  → product_db
 * Cache        : Hazelcast (embedded, port 5703)
 * Messaging    : Kafka → product-events topic (producer + consumer)
 * Security     : Keycloak JWT (realm_access.roles → ROLE_USER / ROLE_ADMIN)
 * Docs         : http://localhost:2028/swagger-ui/index.html
 *
 * @EnableRetry activates @Retryable on ProductService.deductStock() /
 * restoreStock() for automatic retry on ObjectOptimisticLockingFailureException.
 */
@SpringBootApplication
@EnableCaching
@EnableRetry
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
