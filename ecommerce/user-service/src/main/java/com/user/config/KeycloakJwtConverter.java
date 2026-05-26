package com.user.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import reactor.core.publisher.Flux;

import java.util.*;

/**
 * Keycloak JWT → Spring Security authority converter for User Service (Reactive/WebFlux).
 *
 * Keycloak puts roles in:
 *  - realm_access.roles         → realm-level roles  (USER, ADMIN, …)
 *  - resource_access.<id>.roles → client-level roles
 *
 * Spring Security's default reactive converter only reads 'scope'/'scp' claims.
 * This converter bridges Keycloak's role structure to Spring Security authorities.
 *
 * Usage (in reactive SecurityConfig):
 *   .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
 *       jwt.jwtAuthenticationConverter(KeycloakJwtConverter.reactive())))
 */
public final class KeycloakJwtConverter {

    private KeycloakJwtConverter() {}

    /**
     * Returns a {@link ReactiveJwtAuthenticationConverterAdapter} that wraps the
     * blocking {@link JwtAuthenticationConverter} for use in WebFlux security chains.
     */
    public static ReactiveJwtAuthenticationConverterAdapter reactive() {
        return new ReactiveJwtAuthenticationConverterAdapter(blocking());
    }

    /** Blocking (servlet) variant — reuse when needed. */
    public static JwtAuthenticationConverter blocking() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(KeycloakJwtConverter::extractAuthorities);
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }

    @SuppressWarnings("unchecked")
    private static Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();

        // ── 1. Realm-level roles ─────────────────────────────────────────────
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null) {
            List<String> roles = (List<String>) realmAccess.get("roles");
            if (roles != null) {
                roles.forEach(role ->
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
            }
        }

        // ── 2. Client-level roles (resource_access.<clientId>.roles) ─────────
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess != null) {
            resourceAccess.values().forEach(value -> {
                if (value instanceof Map<?, ?> clientMap) {
                    List<String> clientRoles = (List<String>) clientMap.get("roles");
                    if (clientRoles != null) {
                        clientRoles.forEach(role ->
                            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
                    }
                }
            });
        }

        return authorities;
    }
}
