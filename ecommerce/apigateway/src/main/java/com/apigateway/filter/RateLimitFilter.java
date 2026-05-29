package com.apigateway.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Three-tier rate limiter applied after Spring Security has populated the SecurityContext:
 *
 *   Tier 1 — global per-IP:                 300 req/min  (all callers)
 *   Tier 2a — per-user, POST /api/v1/orders: 100 req/min  (authenticated)
 *   Tier 2b — anonymous per-IP:               60 req/min  (no valid JWT)
 *
 * All three tiers must pass; the tightest limit wins.
 * On rejection: HTTP 429 with Retry-After: 60 and X-RateLimit-Limit headers.
 *
 * Buckets are stored in-memory (ConcurrentHashMap).  For multi-replica deployments
 * swap to Bucket4j-Redis so limits are shared across gateway instances.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int IP_GLOBAL_LIMIT   = 300;
    private static final int ORDER_POST_LIMIT  = 100;
    private static final int ANONYMOUS_LIMIT   =  60;

    private static final String ORDER_PATH = "/api/v1/orders";

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String ip = resolveIp(request);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);

        // Tier 1: global per-IP throttle (absorbs DDoS before per-user checks)
        if (!bucket("ip:" + ip, IP_GLOBAL_LIMIT).tryConsume(1)) {
            reject(response, IP_GLOBAL_LIMIT);
            return;
        }

        if (authenticated) {
            // Tier 2a: protect order-creation from per-user flooding
            if ("POST".equalsIgnoreCase(request.getMethod())
                    && request.getRequestURI().startsWith(ORDER_PATH)) {
                String userId = extractUserId(auth);
                if (!bucket("order:" + userId, ORDER_POST_LIMIT).tryConsume(1)) {
                    reject(response, ORDER_POST_LIMIT);
                    return;
                }
            }
        } else {
            // Tier 2b: tighter cap for unauthenticated callers
            if (!bucket("anon:" + ip, ANONYMOUS_LIMIT).tryConsume(1)) {
                reject(response, ANONYMOUS_LIMIT);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private Bucket bucket(String key, int requestsPerMinute) {
        return buckets.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(
                        requestsPerMinute,
                        Refill.greedy(requestsPerMinute, Duration.ofMinutes(1))))
                .build());
    }

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String extractUserId(Authentication auth) {
        if (auth.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        return auth.getName();
    }

    private void reject(HttpServletResponse response, int limit) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", "60");
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"error\":\"Too many requests\",\"message\":\"Rate limit exceeded. Retry after 60 seconds.\"}");
    }
}
