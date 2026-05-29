package com.apigateway.controller;

import com.apigateway.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

import java.time.Instant;
import java.util.UUID;

/**
 * Handles the forward:/fallback/service-unavailable target used by every
 * CircuitBreaker filter.  Invoked when:
 *   - the circuit is OPEN (too many recent failures)
 *   - a downstream connection cannot be established
 *   - the downstream returned a 5xx status code listed in statusCodes
 *
 * The original request URI is recovered from the servlet forward attribute
 * so the error response tells the client which resource failed.
 */
@RestController
public class FallbackController {

    private static final Logger log = LoggerFactory.getLogger(FallbackController.class);

    @RequestMapping("/fallback/service-unavailable")
    public ResponseEntity<ErrorResponse> serviceUnavailable(HttpServletRequest request) {
        String originalPath = (String) request.getAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE);
        if (originalPath == null) originalPath = request.getRequestURI();

        log.warn("Circuit breaker fallback triggered for path: {}", originalPath);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse(
                Instant.now(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                "The service handling this request is temporarily unavailable. Please try again shortly.",
                originalPath,
                correlationId()
        ));
    }

    private String correlationId() {
        String traceId = MDC.get("traceId");
        return (traceId != null && !traceId.isBlank()) ? traceId : UUID.randomUUID().toString();
    }
}
