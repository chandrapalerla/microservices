package com.apigateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Propagates JWT token from the incoming request to downstream services
 * Adds the Authorization header to all outgoing requests to backend services
 */
@Component
public class TokenPropagationFilter extends OncePerRequestFilter {

    public static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // Check if JWT token is present in the security context
            Object authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                String tokenValue = jwt.getTokenValue();

                // Add token to the request as an attribute for downstream processing
                request.setAttribute("jwt_token", tokenValue);
                request.setAttribute("jwt_token_header", BEARER_PREFIX + tokenValue);
            }
        } catch (Exception e) {
            logger.debug("Could not extract JWT token: " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}

