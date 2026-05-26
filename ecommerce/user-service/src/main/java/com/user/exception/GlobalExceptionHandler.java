package com.user.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralised exception → HTTP response mapping for both WebFlux and MVC contexts.
 *
 * Key classes:
 *  - WebExchangeBindException  — WebFlux equivalent of MethodArgumentNotValidException
 *    (it actually *extends* MethodArgumentNotValidException since Spring 5.3, so one
 *     handler covers both, but we list WebExchangeBindException explicitly so the
 *     more-specific type is matched first in a WebFlux deployment).
 *  - ObjectOptimisticLockingFailureException — thrown by Hibernate when a @Version
 *    mismatch is detected on UPDATE → mapped to HTTP 409 Conflict.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ─── 404 Not Found ────────────────────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(ResourceNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), null);
    }

    // ─── 409 Conflict — optimistic locking ───────────────────────────────────

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Object> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return body(HttpStatus.CONFLICT, "Conflict",
                "The record was modified by another request. Reload and try again.", null);
    }

    // ─── 400 Bad Request — bean validation (WebFlux) ─────────────────────────

    /**
     * WebFlux raises WebExchangeBindException for @Valid failures on @RequestBody.
     * It extends MethodArgumentNotValidException, so this handler catches both.
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<Object> handleWebExchangeBind(WebExchangeBindException ex) {
        List<Map<String, String>> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> {
                    Map<String, String> m = new HashMap<>();
                    m.put("field", err.getField());
                    m.put("message", err.getDefaultMessage());
                    return m;
                })
                .collect(Collectors.toList());

        return body(HttpStatus.BAD_REQUEST, "Bad Request", "Validation failed", errors);
    }

    // ─── 400 Bad Request — bean validation (MVC / cross-context safety) ──────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> {
                    Map<String, String> m = new HashMap<>();
                    m.put("field", err.getField());
                    m.put("message", err.getDefaultMessage());
                    return m;
                })
                .collect(Collectors.toList());

        return body(HttpStatus.BAD_REQUEST, "Bad Request", "Validation failed", errors);
    }

    // ─── 400 Bad Request — constraint violations ─────────────────────────────

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex) {
        List<Map<String, String>> errors = ex.getConstraintViolations()
                .stream()
                .map(cv -> {
                    Map<String, String> m = new HashMap<>();
                    m.put("path", cv.getPropertyPath().toString());
                    m.put("message", cv.getMessage());
                    return m;
                })
                .collect(Collectors.toList());

        return body(HttpStatus.BAD_REQUEST, "Bad Request", "Validation failed", errors);
    }

    // ─── 500 Catch-all ────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleOther(Exception ex) {
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", ex.getMessage(), null);
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private ResponseEntity<Object> body(HttpStatus status, String error,
                                        String message, List<?> errors) {
        Map<String, Object> b = new HashMap<>();
        b.put("timestamp", Instant.now().toString());
        b.put("status", status.value());
        b.put("error", error);
        b.put("message", message);
        if (errors != null) b.put("errors", errors);
        return ResponseEntity.status(status).body(b);
    }
}
