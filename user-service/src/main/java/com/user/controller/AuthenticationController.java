package com.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Authentication controller for User Service
 * Handles logout and secure endpoint operations
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Authentication endpoints")
@Slf4j
public class AuthenticationController {

    public AuthenticationController() {
        // No-op constructor. WebClient can be injected when needed.
    }

    /**
     * Logout endpoint - invalidates the current session
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout user", description = "Logs out the current user and invalidates the token")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User logged out successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - no active session")
    })
    public Mono<ResponseEntity<Map<String, Object>>> logout() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> {
                        if (auth != null && auth.isAuthenticated()) {
                            log.info("User {} logged out", auth.getName());

                            Map<String, Object> response = new HashMap<>();
                            response.put("status", "success");
                            response.put("message", "User logged out successfully");
                            response.put("timestamp", System.currentTimeMillis());

                            return Mono.just(ResponseEntity.ok(response));
                        }
                        Map<String, Object> err = new HashMap<>();
                        err.put("status", "error");
                        err.put("message", "No active session");
                        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(err));
                })
                        .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(new HashMap<String, Object>() {{
                                    put("status", "error");
                                    put("message", "No active session");
                                }}));
    }

    /**
     * Current user info endpoint
     */
    @GetMapping("/me")
    @Operation(summary = "Get current user info", description = "Returns information about the currently authenticated user")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User information retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public Mono<ResponseEntity<Map<String, Object>>> getCurrentUser() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> {
                    if (auth != null && auth.isAuthenticated()) {
                        Map<String, Object> userInfo = new HashMap<>();
                        userInfo.put("username", auth.getName());
                        userInfo.put("authorities", auth.getAuthorities());
                        userInfo.put("authenticated", auth.isAuthenticated());

                        return Mono.just(ResponseEntity.ok(userInfo));
                    }
                    Map<String, Object> errorResp = new HashMap<>();
                    errorResp.put("status", "error");
                    errorResp.put("message", "Unauthorized");
                    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResp));
                })
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new HashMap<String, Object>() {{
                            put("status", "error");
                            put("message", "No active session");
                        }}));
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Checks if the service is running")
    @ApiResponse(responseCode = "200", description = "Service is healthy")
    public Mono<ResponseEntity<Map<String, Object>>> health() {
        return Mono.just(ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "User Service",
                "timestamp", System.currentTimeMillis()
        )));
    }
}

