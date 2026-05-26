package com.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

/**
 * Spring Security configuration for User Service
 * Configures JWT token validation and role-based method-level security with WebFlux
 */
@Configuration
@EnableWebFluxSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
public class SecurityConfig {

    /**
     * Configure security filter chain for JWT authentication with WebFlux
     */
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http) {

        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeExchange(exchanges -> exchanges

                // Public endpoints
                .pathMatchers(
                        "/health/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/*.html"
                ).permitAll()

                // User endpoints require USER or ADMIN role
                .pathMatchers("/api/v1/users/**")
                .hasAnyRole("USER", "ADMIN")

                // All other endpoints require authentication
                .anyExchange()
                .authenticated()
            )

            // OAuth2 Resource Server — use Keycloak JWT converter so
            // realm_access.roles is mapped to ROLE_USER / ROLE_ADMIN authorities
            .oauth2ResourceServer(oauth2 ->
                    oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(KeycloakJwtConverter.reactive())));

        return http.build();
    }

    /**
     * CORS configuration for reactive/WebFlux endpoints
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOrigins(Arrays.asList(
            "http://localhost:3000",
            "http://localhost:4200",
            "http://localhost:2027"  // API Gateway
        ));
        corsConfig.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));
        corsConfig.setAllowedHeaders(Collections.singletonList("*"));
        corsConfig.setExposedHeaders(Arrays.asList(
            "Authorization", "X-Total-Count", "X-Page-Count"
        ));
        corsConfig.setAllowCredentials(true);
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);
        return source;
    }
}

