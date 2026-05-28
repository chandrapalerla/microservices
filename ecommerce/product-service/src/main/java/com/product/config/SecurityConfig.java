package com.product.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Security configuration for the product-service.
 *
 * Two-layer security:
 *  1. GatewaySecretFilter — blocks all /api/** requests that do NOT carry the
 *     shared X-Gateway-Secret header.  This prevents direct access that bypasses
 *     the API Gateway.
 *  2. JWT resource server — validates the Keycloak-issued Bearer token and
 *     maps realm roles to ROLE_USER / ROLE_ADMIN authorities.
 *
 * Public read endpoints (product catalogue) are accessible without authentication.
 * Write / admin endpoints require ROLE_ADMIN.
 * Stock deduct/restore (called by order-service) requires ROLE_USER or ROLE_ADMIN.
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${gateway.internal-secret}")
    private String gatewaySecret;

    // ── Gateway secret guard ──────────────────────────────────────────────────

    /**
     * Blocks any request to /api/** that does not carry the correct X-Gateway-Secret header.
     * The API Gateway always adds this header before forwarding; direct callers lack it.
     */
    static class GatewaySecretFilter extends OncePerRequestFilter {

        private final String expectedSecret;

        GatewaySecretFilter(String expectedSecret) {
            this.expectedSecret = expectedSecret;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            String path = request.getRequestURI();
            if (path.startsWith("/api/")) {
                String header = request.getHeader("X-Gateway-Secret");
                if (!StringUtils.hasText(header) || !header.equals(expectedSecret)) {
                    log.warn("Rejected direct request to {} — missing or invalid X-Gateway-Secret", path);
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
                    return;
                }
            }
            filterChain.doFilter(request, response);
        }
    }

    // ── Security filter chain ─────────────────────────────────────────────────

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Register the gateway-secret guard before JWT processing
            .addFilterBefore(new GatewaySecretFilter(gatewaySecret),
                             UsernamePasswordAuthenticationFilter.class)

            .authorizeHttpRequests(auth -> auth
                // Swagger / OpenAPI — always open
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                                 "/v3/api-docs/**", "/v3/api-docs").permitAll()

                // Actuator — open for Prometheus scraping
                .requestMatchers("/actuator/**").permitAll()

                // Public read-only product catalogue (GET only)
                .requestMatchers(HttpMethod.GET,
                        "/api/v1/products/**",
                        "/api/v1/categories/**").permitAll()

                // Stock operations — internal (order-service via gateway)
                .requestMatchers(HttpMethod.POST,
                        "/api/v1/products/*/deduct-stock",
                        "/api/v1/products/*/restore-stock").hasAnyRole("USER", "ADMIN")

                // All other /api/** write operations require ADMIN
                .requestMatchers("/api/**").hasRole("ADMIN")

                // Anything else
                .anyRequest().authenticated()
            )

            // JWT resource server — validates Bearer token from Keycloak
            .oauth2ResourceServer(rs -> rs
                .jwt(jwt -> jwt.jwtAuthenticationConverter(KeycloakJwtConverter.blocking()))
            );

        return http.build();
    }
}
