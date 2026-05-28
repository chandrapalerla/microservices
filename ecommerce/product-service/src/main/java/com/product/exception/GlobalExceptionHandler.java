package com.product.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
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

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        ProblemDetail p = problem(HttpStatus.NOT_FOUND, "Resource Not Found", ex.getMessage(), "not-found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(p);
    }

    @ExceptionHandler(DuplicateSkuException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateSku(DuplicateSkuException ex) {
        log.warn("Duplicate SKU: {}", ex.getMessage());
        ProblemDetail p = problem(HttpStatus.CONFLICT, "Duplicate SKU", ex.getMessage(), "duplicate-sku");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(p);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ProblemDetail> handleInsufficientStock(InsufficientStockException ex) {
        log.warn("Insufficient stock: {}", ex.getMessage());
        ProblemDetail p = problem(HttpStatus.CONFLICT, "Insufficient Stock", ex.getMessage(), "insufficient-stock");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(p);
    }

    @ExceptionHandler(CategoryInUseException.class)
    public ResponseEntity<ProblemDetail> handleCategoryInUse(CategoryInUseException ex) {
        log.warn("Category in use: {}", ex.getMessage());
        ProblemDetail p = problem(HttpStatus.CONFLICT, "Category In Use", ex.getMessage(), "category-in-use");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(p);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("Concurrent stock update collision on {}: {}", ex.getPersistentClassName(), ex.getMessage());
        ProblemDetail p = problem(HttpStatus.CONFLICT, "Concurrent Modification",
                "The product was modified by a concurrent request (e.g. simultaneous stock deduction). " +
                "Fetch the latest version and retry.", "concurrent-modification");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(p);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (a, b) -> a));
        ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "Validation Error",
                "Request validation failed", "validation");
        p.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(p);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        ProblemDetail p = problem(HttpStatus.CONFLICT, "Data Integrity Violation",
                "A record with the same unique key already exists", "conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(p);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail p = problem(HttpStatus.FORBIDDEN, "Access Denied",
                "You do not have permission to perform this action", "forbidden");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(p);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        ProblemDetail p = problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred. Please try again later.", "internal");
        return ResponseEntity.internalServerError().body(p);
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, String errorCode) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle(title);
        p.setType(URI.create("https://api.ecommerce.example.com/errors/" + errorCode));
        p.setProperty("timestamp", Instant.now());
        return p;
    }
}
