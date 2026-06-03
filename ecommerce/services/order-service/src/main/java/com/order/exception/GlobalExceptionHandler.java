package com.order.exception;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralised exception → HTTP response mapping.
 *
 * Uses RFC 7807 ProblemDetail (built into Spring 6 / Boot 3) for consistent
 * error shapes across all endpoints.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── 404 Not Found ─────────────────────────────────────────────────────────
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource Not Found");
        problem.setType(URI.create("https://api.ecommerce.example.com/errors/not-found"));
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    // ── 400 Bad Request — invalid state transition ────────────────────────────
    @ExceptionHandler(InvalidOrderTransitionException.class)
    public ResponseEntity<ProblemDetail> handleInvalidTransition(InvalidOrderTransitionException ex) {
        log.warn("Invalid order transition: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid Order Transition");
        problem.setType(URI.create("https://api.ecommerce.example.com/errors/invalid-transition"));
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.badRequest().body(problem);
    }

    // ── 409 Conflict — insufficient stock ─────────────────────────────────────
    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ProblemDetail> handleInsufficientStock(InsufficientStockException ex) {
        log.warn("Insufficient stock: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Insufficient Stock");
        problem.setType(URI.create("https://api.ecommerce.example.com/errors/insufficient-stock"));
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    // ── 400 Bad Request — unreadable / unparseable request body ──────────────
    // Covers: unknown enum values, malformed JSON, type mismatches.
    // Without this, the catch-all returns 500 which trips the gateway circuit breaker.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Unreadable request body: {}", ex.getMostSpecificCause().getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMostSpecificCause().getMessage());
        problem.setTitle("Invalid Request Body");
        problem.setType(URI.create("https://api.ecommerce.example.com/errors/bad-request"));
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.badRequest().body(problem);
    }

    // ── 400 Bad Request — Bean Validation failures ────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (existing, replacement) -> existing
                ));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Validation Error");
        problem.setType(URI.create("https://api.ecommerce.example.com/errors/validation"));
        problem.setProperty("errors", errors);
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.badRequest().body(problem);
    }

    // ── 409 Conflict — DB unique/FK constraint violation ─────────────────────
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "A record with the same unique key already exists");
        problem.setTitle("Data Integrity Violation");
        problem.setType(URI.create("https://api.ecommerce.example.com/errors/conflict"));
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    // ── 409 Conflict — optimistic locking (concurrent update) ─────────────────
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("Optimistic locking failure for {}: {}", ex.getPersistentClassName(), ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "The order was modified by another request. Fetch the latest version and retry.");
        problem.setTitle("Concurrent Modification");
        problem.setType(URI.create("https://api.ecommerce.example.com/errors/concurrent-modification"));
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    // ── 403 Forbidden — security access denied ────────────────────────────────
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "You do not have permission to perform this action");
        problem.setTitle("Access Denied");
        problem.setType(URI.create("https://api.ecommerce.example.com/errors/forbidden"));
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    // ── 400 Bad Request — coupon validation failures ──────────────────────────
    @ExceptionHandler(CouponException.class)
    public ResponseEntity<ProblemDetail> handleCoupon(CouponException ex) {
        log.warn("Coupon validation failed: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Coupon Error");
        problem.setType(URI.create("https://api.ecommerce.example.com/errors/coupon"));
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.badRequest().body(problem);
    }

    // ── Feign client errors (downstream service responses) ────────────────────
    //
    // Rules:
    //  4xx from downstream → pass the same 4xx back to the caller.
    //       These are data/auth errors, NOT infrastructure failures.
    //       Returning 502 here would cause the API gateway's circuit breaker
    //       to trip on client mistakes, which is wrong.
    //  5xx or connection error (status == -1) → 502 Bad Gateway.
    //       These represent a real downstream infrastructure failure.
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ProblemDetail> handleFeignException(FeignException ex) {
        int status = ex.status();
        log.error("Downstream service error (status {}): {}", status, ex.getMessage());

        if (status == 401 || status == 403) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.valueOf(status),
                    "Not authorised to access a required downstream resource");
            problem.setTitle(status == 401 ? "Unauthorized" : "Forbidden");
            problem.setProperty("timestamp", Instant.now());
            return ResponseEntity.status(status).body(problem);
        }

        if (status == 404) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.NOT_FOUND, "Referenced resource not found in downstream service");
            problem.setTitle("Downstream Resource Not Found");
            problem.setProperty("timestamp", Instant.now());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
        }

        if (status == 409) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT, "Conflict reported by downstream service");
            problem.setTitle("Downstream Conflict");
            problem.setProperty("timestamp", Instant.now());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
        }

        if (status == 503 || ex instanceof FeignException.ServiceUnavailable) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "A dependent service is currently unavailable. Please retry.");
            problem.setTitle("Service Unavailable");
            problem.setProperty("timestamp", Instant.now());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
        }

        // 5xx or no HTTP response (connection refused, timeout — status == -1)
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY,
                "An error occurred while communicating with a downstream service");
        problem.setTitle("Bad Gateway");
        problem.setProperty("downstreamStatus", status);
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(problem);
    }

    // ── 500 Internal Server Error — catch-all ─────────────────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again later.");
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://api.ecommerce.example.com/errors/internal"));
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.internalServerError().body(problem);
    }
}
