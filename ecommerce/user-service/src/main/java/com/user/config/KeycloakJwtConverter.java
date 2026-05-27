package com.user.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.*;

/**
 * Keycloak JWT → Spring Security authority converter.
 *
 * Keycloak puts roles in:
 *  - realm_access.roles              → realm-level roles  (USER, ADMIN, …)
 *  - resource_access.&lt;id&gt;.roles → client-level roles
 *
 * Spring Security's default converter only reads 'scope'/'scp' claims.
 * This converter bridges Keycloak's role structure to Spring Security authorities
 * (e.g. "ADMIN" → "ROLE_ADMIN").
 *
 * Usage (in MVC SecurityConfig):
 *   .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
 *       jwt.jwtAuthenticationConverter(KeycloakJwtConverter.blocking())))
 */
public final class KeycloakJwtConverter {

    private KeycloakJwtConverter() {}

    /** Servlet/MVC converter — use in {@code HttpSecurity} OAuth2 resource-server config. */
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
