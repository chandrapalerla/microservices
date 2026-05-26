package com.user.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Provides a blocking {@link JwtDecoder} for use by {@link com.user.util.JwtUtility}.
 *
 * NOTE: Spring Boot's reactive auto-configuration already registers a bean named
 * 'jwtDecoder' (a ReactiveJwtDecoder).  This bean is intentionally named
 * 'blockingJwtDecoder' to avoid a BeanDefinitionOverrideException.
 * JwtUtility is injected by type (JwtDecoder), so it will receive this bean automatically
 * since it is the only JwtDecoder (blocking) in the context.
 */
@Configuration
public class JwtConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Bean("blockingJwtDecoder")
    public JwtDecoder blockingJwtDecoder() {
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }
}
