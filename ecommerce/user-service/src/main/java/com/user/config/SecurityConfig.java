package com.user.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Security for user-service — two layers of protection:
 *
 *  Layer 1 │ gatewaySecretFilter
 *          │ Every /api/** request must carry X-Gateway-Secret (added by GatewayConfig).
 *          │ Direct calls from browsers/curl get 403 before touching any business logic.
 *
 *  Layer 2 │ JWT resource-server (defence-in-depth)
 *          │ Re-validates the Bearer token even though the gateway already validated it.
 *          │ Guards against misconfigured network policies that might bypass the gateway.
 *
 * NO CORS config here — CORS only applies when a browser contacts a server directly.
 * Browsers always go through the gateway (:2027), which owns the CORS configuration.
 * Server-to-server calls (gateway → this service) never trigger CORS preflight.
 */
@Configuration
@EnableWebFluxSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
public class SecurityConfig {

    /** Must match gateway.internal-secret in apigateway/application.yaml */
    @Value("${gateway.internal-secret}")
    private String gatewaySecret;

    /**
     * Layer 1 — blocks direct access, passes gateway-forwarded requests through.
     */
    @Bean
    public WebFilter gatewaySecretFilter() {
        return (ServerWebExchange exchange, WebFilterChain chain) -> {
            String path = exchange.getRequest().getPath().value();

            if (path.startsWith("/api/")) {
                String incoming = exchange.getRequest()
                        .getHeaders()
                        .getFirst("X-Gateway-Secret");

                if (!gatewaySecret.equals(incoming)) {
                    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                    return exchange.getResponse().setComplete();
                }
            }
            return chain.filter(exchange);
        };
    }

    /**
     * Layer 2 — JWT validation + role-based access rules.
     */
    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .cors(ServerHttpSecurity.CorsSpec::disable)   // gateway owns CORS, not this service
            .authorizeExchange(exchanges -> exchanges
                // Swagger UI served directly — no token or gateway secret needed
                .pathMatchers(
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                ).permitAll()

                // Business endpoints — roles enforced at gateway AND here (defence-in-depth)
                .pathMatchers("/api/v1/users/**").hasAnyRole("USER", "ADMIN")
                .pathMatchers("/api/v1/auth/**").authenticated()

                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 ->
                    oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(KeycloakJwtConverter.reactive())));

        return http.build();
    }
}
