package com.user.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {

        final String securitySchemeName = "keycloak";
        return new OpenAPI()
                .info(new Info()
                        .title("User Service API")
                        .version("1.0.0")
                        .description("User Service REST API with Keycloak authentication")
                        .contact(new Contact()
                                .name("API Support")
                                .email("support@example.com"))
                        .license(new License()
                                .name("Apache 2.0")))
                .addServersItem(new Server()
                        .url("http://localhost:2026")
                        .description("Local Development Server"))
                .addSecurityItem(
                        new SecurityRequirement()
                                .addList(securitySchemeName)
                )
                .schemaRequirement(
                        securitySchemeName,
                        new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.OAUTH2)
                                .flows(
                                        new OAuthFlows()
                                                .authorizationCode(
                                                     new OAuthFlow()
                                                        .authorizationUrl(
                                                                "http://localhost:30080/realms/microservices-realm/protocol/openid-connect/auth"
                                                        )
                                                        .tokenUrl(
                                                                "http://localhost:30080/realms/microservices-realm/protocol/openid-connect/token"
                                                        )
                                                        .scopes(
                                                                new Scopes()
                                                                        .addString("openid", "OpenID scope")
                                                        )
                                                )
                                )
                );
    }
}