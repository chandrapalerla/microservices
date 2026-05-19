package com.mysqlmongodb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.servers.Server;

@SpringBootApplication
@EnableCaching
@OpenAPIDefinition(
	info = @Info(
		title = "MySQL-MongoDB Microservice API",
		version = "1.0.0",
		description = "REST API for managing Users (MySQL) and Orders (MongoDB) with caching, versioning, validation, and pagination",
		contact = @Contact(
			name = "API Support",
			email = "support@example.com"
		)
	),
	servers = {
		@Server(
			url = "http://localhost:2026",
			description = "Local Development Server"
		)
	}
)
public class UserServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(UserServiceApplication.class, args);
	}

}
