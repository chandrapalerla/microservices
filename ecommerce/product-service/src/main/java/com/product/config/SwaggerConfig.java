package com.product.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI configuration.
 * Swagger UI: http://localhost:2028/swagger-ui/index.html
 * API docs:   http://localhost:2028/v3/api-docs
 */
@Configuration
public class SwaggerConfig {

    private static final String BEARER_AUTH = "BearerAuth";

    @Bean
    public OpenAPI productServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Product Service API")
                        .description("Product catalogue management — products, categories, stock, and search")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Ecommerce Platform Team")
                                .email("platform@ecommerce.example.com")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH,
                                new SecurityScheme()
                                        .name(BEARER_AUTH)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Keycloak JWT access token. " +
                                                "Obtain from: POST /realms/microservices-realm/protocol/openid-connect/token")));
    }
}
