package com.product.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converts a Keycloak-issued JWT into a Spring Security authentication token.
 *
 * Keycloak puts roles in:
 *   - realm_access.roles          (realm-wide roles: USER, ADMIN)
 *   - resource_access.<id>.roles  (client-specific roles, optional)
 *
 * Spring Security's default converter only reads "scope"/"scp" claims.
 * This converter combines both sources and prefixes every role with "ROLE_".
 *
 * Usage (Servlet / Spring MVC):
 *   http.oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(
 *       KeycloakJwtConverter.blocking())));
 */
public final class KeycloakJwtConverter {

    private KeycloakJwtConverter() {}

    /**
     * Returns a blocking (Servlet / Spring MVC) JwtAuthenticationConverter
     * that maps Keycloak roles → ROLE_xxx authorities.
     */
    public static Converter<Jwt, AbstractAuthenticationToken> blocking() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(keycloakRolesConverter());
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }

    // ── Role extraction ───────────────────────────────────────────────────────

    private static Converter<Jwt, Collection<GrantedAuthority>> keycloakRolesConverter() {
        JwtGrantedAuthoritiesConverter defaultConverter = new JwtGrantedAuthoritiesConverter();

        return jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>(defaultConverter.convert(jwt));
            authorities.addAll(extractRealmRoles(jwt));
            authorities.addAll(extractResourceRoles(jwt));
            return authorities;
        };
    }

    @SuppressWarnings("unchecked")
    private static List<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) return List.of();
        Object rolesObj = realmAccess.get("roles");
        if (!(rolesObj instanceof List)) return List.of();
        return ((List<String>) rolesObj).stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private static List<GrantedAuthority> extractResourceRoles(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess == null) return List.of();
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (Object clientAccess : resourceAccess.values()) {
            if (!(clientAccess instanceof Map)) continue;
            Object rolesObj = ((Map<String, Object>) clientAccess).get("roles");
            if (!(rolesObj instanceof List)) continue;
            ((List<String>) rolesObj).forEach(role ->
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
        }
        return authorities;
    }
}
