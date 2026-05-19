package com.mysqlmongodb.config;

import org.springframework.context.annotation.Configuration;

/**
 * Hazelcast caching is auto-configured via spring-boot-starter for Hazelcast.
 * Cache names are defined in service layer with @Cacheable annotations:
 * - users: cache for individual user lookups
 * - usersPage: cache for paginated user requests
 * - orders: cache for individual order lookups
 * - ordersPage: cache for paginated order requests
 *
 * Default TTL: 10 minutes (can be customized via application.properties)
 * Default eviction policy: LRU (Least Recently Used)
 */
@Configuration
public class CacheConfig {
    // Hazelcast auto-configuration handles everything via spring-boot-starter
}

