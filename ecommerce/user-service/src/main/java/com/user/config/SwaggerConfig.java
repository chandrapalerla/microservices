package com.user.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration.
 *
 * SERVER URL is set to the API Gateway (port 2027), NOT directly to this service.
 * Reason: every request made by Swagger "Try it out" must go through the gateway so
 * that the gateway can add the X-Gateway-Secret header.  Calls sent directly to
 * port 2026 are rejected by gatewaySecretFilter with HTTP 403.
 *
 * SECURITY SCHEME uses Keycloak's authorization_code + PKCE flow.
 * Click "Authorize" in Swagger UI → Keycloak login page → token stored in browser →
 * every "Try it out" call includes Authorization: Bearer <token>.
 */
@Configuration
public class SwaggerConfig {

    private static final String KEYCLOAK_BASE =
            "http://localhost:30080/realms/microservices-realm/protocol/openid-connect";

    @Value("${server.port:2026}")
    private String servicePort;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(apiInfo())
                // ── Servers ──────────────────────────────────────────────────
                // Primary: always test through the gateway
                .addServersItem(new Server()
                        .url("http://localhost:2027")
                        .description("API Gateway (use this for Try-it-out)"))
                // Secondary: direct access — only works without gatewaySecretFilter
                //            useful for service-level unit/integration tests
                .addServersItem(new Server()
                        .url("http://localhost:" + servicePort)
                        .description("Direct (bypasses gateway — blocked in production)"))
                // ── Security: apply keycloak scheme to every endpoint globally ──
                .addSecurityItem(new SecurityRequirement().addList("keycloak"))
                .schemaRequirement("keycloak", keycloakScheme());
    }

    // ── API metadata ──────────────────────────────────────────────────────────
    private Info apiInfo() {
        return new Info()
                .title("User Service API")
                .version("1.0.0")
                .description("CRUD API for User management. Secured via Keycloak JWT. "
                        + "Click Authorize to log in and test endpoints.")
                .contact(new Contact()
                        .name("API Support")
                        .email("support@example.com"));
    }

    // ── Keycloak OAuth2 security scheme (authorization_code + PKCE) ───────────
    private SecurityScheme keycloakScheme() {
        return new SecurityScheme()
                .name("keycloak")
                .type(SecurityScheme.Type.OAUTH2)
                .flows(new OAuthFlows()
                        .authorizationCode(new OAuthFlow()
                                .authorizationUrl(KEYCLOAK_BASE + "/auth")
                                .tokenUrl(KEYCLOAK_BASE + "/token")
                                // Scopes available in Keycloak realm
                                .scopes(new Scopes()
                                        .addString("openid", "OpenID Connect")
                                        .addString("profile", "User profile")
                                        .addString("email", "Email address"))));
    }
}
