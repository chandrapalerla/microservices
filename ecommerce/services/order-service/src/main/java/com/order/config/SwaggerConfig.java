package com.order.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI configuration.
 *
 * UI:   http://localhost:2029/swagger-ui/index.html
 * JSON: http://localhost:2029/v3/api-docs
 *
 * Bearer authentication is pre-configured — paste your Keycloak token in
 * "Authorize" to test secured endpoints directly from the UI.
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI orderServiceOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Order Service API")
                        .description("Order management microservice — place, track, and manage e-commerce orders")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("E-Commerce Platform")
                                .email("support@ecommerce.example.com")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Keycloak JWT — obtain from /auth/token via the API gateway")));
    }
}
