package com.apigateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Emits one structured JSON audit event per request via the "AUDIT" logger.
 *
 * Runs after Spring Security (so SecurityContext is populated for userId) but
 * before RateLimitFilter (default Ordered.LOWEST_PRECEDENCE), ensuring the
 * logged status reflects rate-limit rejections (429) too.
 *
 * Every response also receives X-Correlation-Id set to the active OTel trace ID,
 * so callers can paste it straight into Jaeger/Tempo to find the matching trace.
 *
 * Sample JSON output:
 * {
 *   "event":"REQUEST", "method":"POST", "path":"/api/v1/orders",
 *   "status":201, "durationMs":43, "userId":"a1b2-...", "clientIp":"10.0.0.5",
 *   "traceId":"abc123", "spanId":"def456"
 * }
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class AuditLogFilter extends OncePerRequestFilter {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Prometheus scrapes and k8s health probes are noise — skip them
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        long start = System.currentTimeMillis();

        // Stamp the trace ID on the response so callers can correlate with Jaeger/Tempo.
        // MDC.traceId is populated by micrometer-tracing-bridge-otel before this filter runs.
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            response.setHeader("X-Correlation-Id", traceId);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            audit.info("REQUEST",
                    kv("event",        "REQUEST"),
                    kv("method",       request.getMethod()),
                    kv("path",         request.getRequestURI()),
                    kv("query",        sanitizeQuery(request.getQueryString())),
                    kv("status",       response.getStatus()),
                    kv("durationMs",   System.currentTimeMillis() - start),
                    kv("userId",       resolveUserId()),
                    kv("clientIp",     resolveIp(request)),
                    kv("userAgent",    request.getHeader("User-Agent")),
                    kv("requestSize",  parseLong(request.getHeader("Content-Length"))),
                    kv("responseSize", parseLong(response.getHeader("Content-Length")))
            );
        }
    }

    private String resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return "anonymous";
        }
        // sub claim is the Keycloak user UUID — stable and not a PII display name
        if (auth.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        return auth.getName();
    }

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Masks sensitive query parameters so tokens and passwords never appear in logs.
     * Matches token=, password=, secret=, key=, access_token= (case-insensitive).
     */
    private String sanitizeQuery(String query) {
        if (query == null || query.isBlank()) return null;
        return query.replaceAll("(?i)(token|password|secret|key|access_token)=[^&]*", "$1=***");
    }

    private long parseLong(String header) {
        if (header == null) return -1;
        try { return Long.parseLong(header); }
        catch (NumberFormatException ignored) { return -1; }
    }
}
