package com.apigateway.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Authentication controller for API Gateway
 * Handles login, logout, and token refresh operations
 */
@RestController
@RequestMapping("/auth")
public class AuthenticationController {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationController.class);

    /**
     * Login endpoint - redirects to Keycloak login
     */
    @GetMapping("/login")
    public void login() {
        // Redirect handled by Spring Security OAuth2
    }

    /**
     * Logout endpoint - clears security context and logs out from Keycloak
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        try {
            // Get the current authentication
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            if (auth != null) {
                // Clear the security context
                SecurityContextHolder.clearContext();

                // Invalidate session
                request.getSession().invalidate();

                Map<String, String> responseMap = new HashMap<>();
                responseMap.put("status", "success");
                responseMap.put("message", "User logged out successfully");

                logger.info("User {} logged out", auth.getName());
                return ResponseEntity.ok(responseMap);
            }

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "No active session"));

        } catch (Exception e) {
                logger.error("Error during logout", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "gateway", "API Gateway is running"));
    }
}

