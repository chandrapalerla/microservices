package com.user.config;

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
 * Security for user-service — two layers of protection:
 *
 *  Layer 1 │ GatewaySecretFilter
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

                // Business endpoints — roles enforced at gateway AND here (defence-in-depth)
                .requestMatchers("/api/v1/users/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers("/api/v1/auth/**").authenticated()

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
     * Defined as a private static class so it is NOT auto-registered as a servlet
     * filter by Spring Boot — it is only active inside the security filter chain.
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
