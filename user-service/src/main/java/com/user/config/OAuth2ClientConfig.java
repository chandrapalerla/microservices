package com.user.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Simplified OAuth2 client configuration for service-to-service communication.
 * This implementation provides a request filter that adds a bearer token to outgoing requests.
 * In production replace `getClientCredentialsToken()` with a proper client-credentials implementation.
 */
@Configuration
public class OAuth2ClientConfig {

    @Value("${spring.security.oauth2.client.registration.keycloak.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-secret}")
    private String clientSecret;

    @Value("${spring.security.oauth2.client.provider.keycloak.token-uri}")
    private String tokenUri;

    @Bean
    public WebClient webClientWithOAuth2() {
        return WebClient.builder()
                .filter(oauth2Filter())
                .build();
    }

    private ExchangeFilterFunction oauth2Filter() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest ->
                getClientCredentialsToken()
                        .map(token -> {
                            if (token == null || token.isBlank()) return clientRequest;
                            return ClientRequest.from(clientRequest)
                                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                    .build();
                        })
                        .defaultIfEmpty(clientRequest)
        );
    }

    private Mono<String> getClientCredentialsToken() {
        // Placeholder implementation. Replace with actual token retrieval.
        return Mono.justOrEmpty("client-credentials-token");
    }
}

