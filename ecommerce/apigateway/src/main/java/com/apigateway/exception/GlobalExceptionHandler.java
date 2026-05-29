package com.apigateway.exception;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Micrometer tracing — optional: present when micrometer-tracing-bridge-otel is on the classpath
    @Autowired(required = false)
    private Tracer tracer;

    // ── 503 — downstream service is unreachable ───────────────────────────────
    // Thrown by Spring's RestClient when the TCP connection to an upstream fails
    // (ConnectException, SocketTimeoutException, etc.).
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ErrorResponse> handleResourceAccess(ResourceAccessException ex,
                                                               HttpServletRequest req) {
        log.error("Downstream unreachable [{}]: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE,
                "Service temporarily unavailable. Please try again later.", req);
    }

    // ── 502 — upstream returned a 5xx ────────────────────────────────────────
    // Surfaces when AuthenticationController's RestClient calls Keycloak and
    // Keycloak itself is broken (as opposed to unreachable).
    @ExceptionHandler(HttpServerErrorException.class)
    public ResponseEntity<ErrorResponse> handleServerError(HttpServerErrorException ex,
                                                            HttpServletRequest req) {
        log.error("Upstream server error {} [{}]: {}",
                ex.getStatusCode().value(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY,
                "An upstream service returned an error. Please try again later.", req);
    }

    // ── 4xx pass-through — preserve the status the downstream sent ───────────
    // Keeps 401/404/422 etc. semantically correct rather than swallowing them
    // into a generic 500.
    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ErrorResponse> handleClientError(HttpClientErrorException ex,
                                                            HttpServletRequest req) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        log.debug("Client error {} [{}]", status.value(), req.getRequestURI());
        return build(status, status.getReasonPhrase(), req);
    }

    // ── pass-through — respects @ResponseStatus and Spring-thrown codes ──────
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex,
                                                               HttpServletRequest req) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return build(status, message, req);
    }

    // ── 403 — missing role or method-level security rejection ────────────────
    // Note: AccessDeniedException thrown inside a filter is handled by Spring
    // Security's ExceptionTranslationFilter before reaching this advice.
    // This catches denials thrown from within @Controller methods.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                             HttpServletRequest req) {
        log.warn("Access denied [{} {}]: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN,
                "You do not have permission to access this resource.", req);
    }

    // ── 500 — catch-all ───────────────────────────────────────────────────────
    // Full stack trace is logged server-side; nothing sensitive reaches the client.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception [{} {}]", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please contact support if the problem persists.", req);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message,
                                                  HttpServletRequest req) {
        return ResponseEntity.status(status).body(new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                req.getRequestURI(),
                correlationId()
        ));
    }

    /**
     * Returns the current OTel trace ID so the client's correlationId maps directly
     * to the span visible in Jaeger/Tempo.  Falls back to a random UUID when tracing
     * is not active (e.g. tests, local dev without a collector).
     */
    private String correlationId() {
        if (tracer != null) {
            var span = tracer.currentSpan();
            if (span != null) {
                return span.context().traceId();
            }
        }
        return UUID.randomUUID().toString();
    }
}
