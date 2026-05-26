package com.apigateway.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.*;

/**
 * Keycloak JWT → Spring Security authority converter for API Gateway (Servlet/MVC).
 *
 * Keycloak puts roles in two places inside the JWT:
 *  - realm_access.roles          → realm-level roles (e.g. USER, ADMIN)
 *  - resource_access.<id>.roles  → client-level roles
 *
 * Spring Security's default converter only reads 'scope' / 'scp', so without this
 * converter every request gets 403 even when the user has the correct Keycloak role.
 *
 * Usage (in SecurityConfig):
 *   .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
 *       jwt.jwtAuthenticationConverter(KeycloakJwtConverter.create())))
 */
public final class KeycloakJwtConverter {

    private KeycloakJwtConverter() {}

    /** Build a ready-to-use {@link JwtAuthenticationConverter}. */
    public static JwtAuthenticationConverter create() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(KeycloakJwtConverter::extractAuthorities);
        // Use Keycloak's 'preferred_username' as principal name instead of the opaque 'sub' UUID
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
