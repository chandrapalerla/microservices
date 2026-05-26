package com.user.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

/**
 * HTTP Interceptor for propagating JWT tokens to downstream service calls
 * Automatically adds Authorization header with JWT token to outgoing requests
 */
@Configuration
@Slf4j
public class HttpClientConfig implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(@NonNull HttpRequest request, @NonNull byte[] body, @NonNull ClientHttpRequestExecution execution)
            throws IOException {
        try {
            // Get JWT token from security context
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                String tokenValue = jwt.getTokenValue();
                // Add Authorization header
                request.getHeaders().add(HttpHeaders.AUTHORIZATION, "Bearer " + tokenValue);
                log.debug("JWT token added to outgoing request to: {}", request.getURI());
            }
        } catch (Exception e) {
            log.debug("Could not add JWT token to request: {}", e.getMessage());
            // Continue without token if unable to extract
        }
        return execution.execute(request, body);
    }

    /**
     * Create RestTemplate with JWT token propagation
     */
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(this);
        return restTemplate;
    }
}

