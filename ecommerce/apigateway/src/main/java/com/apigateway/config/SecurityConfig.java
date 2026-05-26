package com.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.apigateway.filter.TokenPropagationFilter;

import java.util.Arrays;
import java.util.Collections;

/**
 * Spring Security configuration for API Gateway
 * Configures JWT token validation, role-based authorization, and CORS
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Configure security filter chain for JWT authentication
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, TokenPropagationFilter tokenPropagationFilter)
            throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(authz -> authz
                // Public endpoints (gateway-level health & docs)
                .requestMatchers(
                        "/auth/login",
                        "/auth/health",
                        "/health",
                        "/actuator/health",
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                ).permitAll()
                // User service endpoints require USER or ADMIN role
                .requestMatchers("/api/v1/users/**").hasAnyRole("USER", "ADMIN")
                // Auth endpoints (user-service login/logout/me) require authentication
                .requestMatchers("/api/v1/auth/**").authenticated()
                // All other endpoints require authentication
                .anyRequest().authenticated()
            )
            // Use Keycloak JWT converter so realm_access.roles → ROLE_USER / ROLE_ADMIN
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                jwt.jwtAuthenticationConverter(KeycloakJwtConverter.create())))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // Add token propagation filter
        http.addFilterAfter(tokenPropagationFilter, BasicAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS configuration
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000", "http://localhost:4200"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Collections.singletonList("*"));
        configuration.setExposedHeaders(Arrays.asList("Authorization", "X-Total-Count"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

