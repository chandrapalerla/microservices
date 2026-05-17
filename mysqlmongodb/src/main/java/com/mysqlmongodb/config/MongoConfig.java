package com.mysqlmongodb.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * MongoDB Configuration
 * This configuration allows MongoDB to be optional - the application can start
 * even if MongoDB is not available. MongoDB operations will fail only when
 * actually attempting to access MongoDB repositories.
 */
@Configuration
@EnableMongoRepositories(basePackages = "com.mysqlmongodb.mongodb.repository")
public class MongoConfig {
    // MongoDB configuration - allowing lazy initialization
    // This prevents the app from crashing if MongoDB is not available at startup
}

