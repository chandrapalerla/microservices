package com.user.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Authorization aspect for method-level security
 * Provides custom authorization logic for sensitive operations
 */
@Aspect
@Component
@Slf4j
public class AuthorizationAspect {

    /**
     * Secure operation - requires ADMIN role
     */
    @Around("@annotation(com.user.aspect.RequiresAdmin)")
    public Object requiresAdmin(ProceedingJoinPoint joinPoint) throws Throwable {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Unauthorized access attempt to admin operation");
            throw new SecurityException("Unauthorized: Authentication required");
        }

        boolean hasAdminRole = authentication.getAuthorities().stream()
               .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
        if (!hasAdminRole) {
            log.warn("Access denied for user: {} - insufficient permissions", authentication.getName());
            throw new SecurityException("Forbidden: Admin role required");
        }
        log.info("Admin operation executed by user: {}", authentication.getName());
        return joinPoint.proceed();
    }

    /**
     * Secure operation - requires USER or ADMIN role
     */
    @Around("@annotation(com.user.aspect.RequiresUser)")
    public Object requiresUser(ProceedingJoinPoint joinPoint) throws Throwable {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Unauthorized access attempt to user operation");
            throw new SecurityException("Unauthorized: Authentication required");
        }
        boolean hasUserRole = authentication.getAuthorities().stream()
                .anyMatch(auth -> {
                    String authority = auth.getAuthority();
                    return authority.equals("ROLE_USER") || authority.equals("ROLE_ADMIN");
                });
        if (!hasUserRole) {
            log.warn("Access denied for user: {} - insufficient permissions", authentication.getName());
            throw new SecurityException("Forbidden: User role required");
        }
        log.info("User operation executed by user: {}", authentication.getName());
        return joinPoint.proceed();
    }
}

/**
 * Custom exception for security violations
 */
class SecurityException extends RuntimeException {
    public SecurityException(String message) {
        super(message);
    }
    public SecurityException(String message, Throwable cause) {
        super(message, cause);
    }
}

