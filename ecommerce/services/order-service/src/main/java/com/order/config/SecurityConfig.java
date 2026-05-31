package com.order.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Security for order-service — mirrors user-service exactly (two defence layers).
 *
 *  Layer 1 │ GatewaySecretFilter
 *          │ Every /api/** request must carry X-Gateway-Secret (stamped by GatewayConfig).
 *          │ Direct browser/curl calls get 403 before touching any business logic.
 *
 *  Layer 2 │ JWT resource-server (defence-in-depth)
 *          │ Re-validates the Bearer token even though the gateway already validated it.
 *          │ Guards against misconfigured network policies that might bypass the gateway.
 *
 * NO CORS config here — CORS is handled by the API gateway which owns the browser boundary.
 * Server-to-server calls never trigger CORS preflight.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
public class SecurityConfig {

    /** Must match gateway.internal-secret in apigateway/application.yaml */
    @Value("${gateway.internal-secret}")
    private String gatewaySecret;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Layer 1: block direct (non-gateway) calls to /api/**
            .addFilterBefore(new GatewaySecretFilter(gatewaySecret),
                             UsernamePasswordAuthenticationFilter.class)

            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)   // gateway owns CORS
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                // Swagger UI — no token or gateway secret needed
                .requestMatchers(
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**"
                ).permitAll()

                // Actuator — open for Prometheus scraping
                .requestMatchers("/actuator/**").permitAll()

                // Order endpoints — roles also enforced at gateway (defence-in-depth)
                .requestMatchers("/api/v1/orders/**").hasAnyRole("USER", "ADMIN")

                // Coupon endpoints — ADMIN-only write/list enforced at method level via @PreAuthorize
                .requestMatchers("/api/v1/coupons/**").hasAnyRole("USER", "ADMIN")

                .anyRequest().authenticated()
            )

            // Layer 2: JWT resource-server validation
            .oauth2ResourceServer(oauth2 ->
                    oauth2.jwt(jwt ->
                            jwt.jwtAuthenticationConverter(KeycloakJwtConverter.blocking())));

        return http.build();
    }

    /**
     * Servlet filter that enforces the shared gateway secret on /api/** requests.
     * Declared as a private static inner class so it is NOT auto-registered as a
     * servlet filter by Spring Boot — it only runs inside the security filter chain.
     */
    private static class GatewaySecretFilter extends OncePerRequestFilter {

        private final String gatewaySecret;

        GatewaySecretFilter(String gatewaySecret) {
            this.gatewaySecret = gatewaySecret;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain)
                throws ServletException, IOException {

            String path = request.getRequestURI();
            if (path.startsWith("/api/")) {
                String incoming = request.getHeader("X-Gateway-Secret");
                if (!gatewaySecret.equals(incoming)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
            }
            filterChain.doFilter(request, response);
        }
    }
}
