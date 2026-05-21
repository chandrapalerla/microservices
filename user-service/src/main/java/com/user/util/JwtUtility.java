package com.user.util;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Lightweight JWT utility that uses Spring Security's JwtDecoder to read claims.
 * This avoids adding direct JJWT dependencies and works with Keycloak-issued tokens.
 */
@Component
@RequiredArgsConstructor
public class JwtUtility {

    private final JwtDecoder jwtDecoder;


    public Map<String, Object> getClaims(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            return jwt.getClaims();
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    public String getSubject(String token) {
        Object sub = getClaims(token).get("sub");
        return sub != null ? sub.toString() : null;
    }

    public String getEmail(String token) {
        Object email = getClaims(token).get("email");
        return email != null ? email.toString() : null;
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        Object realmAccess = getClaims(token).get("realm_access");
        if (realmAccess instanceof Map<?, ?> m) {
            Object roles = m.get("roles");
            if (roles instanceof List<?>) {
                return (List<String>) roles;
            }
        }
        return Collections.emptyList();
    }

    public boolean hasRole(String token, String role) {
        return getRoles(token).contains(role);
    }

    public Date getExpirationDate(String token) {
        Object exp = getClaims(token).get("exp");
        if (exp instanceof Number n) {
            return new Date(n.longValue() * 1000L);
        }
        return null;
    }

    public boolean isTokenExpired(String token) {
        Date exp = getExpirationDate(token);
        return exp == null || exp.before(new Date());
    }

    public Object getClaimValue(String token, String claimName) {
        return getClaims(token).get(claimName);
    }

    public String getPreferredUsername(String token) {
        Object v = getClaimValue(token, "preferred_username");
        return v != null ? v.toString() : null;
    }

    public String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring("Bearer ".length());
        }
        return null;
    }
}

