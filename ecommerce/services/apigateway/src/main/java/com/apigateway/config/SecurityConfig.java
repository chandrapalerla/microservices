package com.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

/**
 * Gateway security: validates incoming JWTs from Keycloak and enforces route-level roles.
 *
 * HOW TO ADD A NEW MICROSERVICE:
 * Add its public paths to the permitAll() block and its secured paths below that.
 * The JWT validation and stateless session policy apply to every route automatically.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                // ── Public (no token needed) ──────────────────────────────
                .requestMatchers(
                        "/auth/health",
                        "/auth/token",      // ROPC login proxy — no token available yet
                        "/auth/refresh",    // silent refresh — called before token attaches
                        "/actuator/**",     // Prometheus scraping + health checks
                        "/fallback/**"      // circuit breaker fallback — servlet FORWARD dispatch
                ).permitAll()

                // ── User service ──────────────────────────────────────────
                .requestMatchers("/api/v1/users/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers("/api/v1/auth/**").authenticated()

                // ── Order service ─────────────────────────────────────────
                .requestMatchers("/api/v1/orders/**").hasAnyRole("USER", "ADMIN")

                // ── Product service ───────────────────────────────────────
                // Public catalogue reads are open; writes require ADMIN (enforced in product-service)
                .requestMatchers("GET", "/api/v1/products/**").permitAll()
                .requestMatchers("GET", "/api/v1/categories/**").permitAll()
                .requestMatchers("/api/v1/products/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers("/api/v1/categories/**").hasRole("ADMIN")

                // ── Payment service ───────────────────────────────────────
                .requestMatchers("/api/v1/payments/**").hasAnyRole("USER", "ADMIN")

                .anyRequest().authenticated()
            )
            // Validate JWT with Keycloak JWKS; map realm_access.roles → ROLE_USER / ROLE_ADMIN
            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(KeycloakJwtConverter.create())));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(
                "http://localhost:5173",    // Vite dev server
                "http://localhost:30500",   // deployed client (K8s NodePort)
                "http://ecommerce.local",   // Ingress hostname
                "http://localhost:2026"     // Swagger UI (user-service) — dev only
        ));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(Collections.singletonList("*"));
        config.setExposedHeaders(Arrays.asList(
                "Authorization", "X-Total-Count",
                "X-RateLimit-Limit", "Retry-After"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
