package com.order.config;

import feign.RequestInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Configures Feign to propagate the caller's JWT and the gateway secret
 * on every outbound service-to-service call.
 *
 * WHY: user-service and product-service both use GatewaySecretFilter — they
 * reject any /api/** request that does not carry X-Gateway-Secret.
 * They also re-validate the JWT as resource servers.
 */
@Configuration
@Slf4j
public class FeignSecurityConfig {

    @Value("${gateway.internal-secret}")
    private String gatewaySecret;

    @Bean
    public RequestInterceptor jwtRelayInterceptor() {
        return requestTemplate -> {
            // Propagate the caller's JWT so downstream services can re-validate it
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
                requestTemplate.header("Authorization", "Bearer " + jwt.getTokenValue());
                log.trace("Propagating JWT to Feign call: {}", requestTemplate.url());
            } else {
                log.trace("No JWT in SecurityContext for Feign call: {}", requestTemplate.url());
            }
            // Add the gateway internal secret so downstream GatewaySecretFilters accept the call
            requestTemplate.header("X-Gateway-Secret", gatewaySecret);
        };
    }
}
