package com.mysqlmongodb.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA/Hibernate Configuration
 * This configuration allows MySQL/JPA to be optional - the application can start
 * even if MySQL is not available. Database operations will fail only when
 * actually attempting to access repositories.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.mysqlmongodb.mysql.repository")
public class JpaConfig {
    // JPA configuration - will allow lazy initialization of repositories
    // This prevents the app from crashing if MySQL is not available at startup
}

