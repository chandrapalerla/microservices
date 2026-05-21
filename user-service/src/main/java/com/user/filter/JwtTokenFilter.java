package com.user.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * JWT Token Filter for User Service
 * Extracts JWT token from request header and validates it
 * Enriches the security context with token claims
 */
@Component
@Slf4j
public class JwtTokenFilter extends OncePerRequestFilter {

    public static final String BEARER_PREFIX = "Bearer ";
    public static final String AUTHORIZATION_HEADER = "Authorization";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // Extract Authorization header
            String authHeader = request.getHeader(AUTHORIZATION_HEADER);

            if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                String token = authHeader.substring(BEARER_PREFIX.length());

                // Get current authentication from security context
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();

                if (auth instanceof JwtAuthenticationToken jwtAuth) {
                    Jwt jwt = jwtAuth.getToken();

                    // Store token information in request attributes for downstream use
                    request.setAttribute("jwt_token", token);
                    request.setAttribute("jwt_subject", jwt.getSubject());
                    request.setAttribute("jwt_claims", jwt.getClaims());

                    // Extract roles from token
                    Collection<String> roles = new ArrayList<>();
                    if (jwt.getClaims().containsKey("realm_access")) {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, java.util.List<String>> realmAccess =
                            (java.util.Map<String, java.util.List<String>>) jwt.getClaims().get("realm_access");
                        if (realmAccess.containsKey("roles")) {
                            roles.addAll(realmAccess.get("roles"));
                        }
                    }
                    request.setAttribute("jwt_roles", roles);
                    log.debug("JWT Token extracted for user: {}", jwt.getSubject());
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract JWT token: {}", e.getMessage());
        }
        filterChain.doFilter(request, response);
    }
}

