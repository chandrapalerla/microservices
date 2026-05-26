package com.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Returns information about the currently authenticated user.
 *
 * WHY only /me here, and no /logout or /health:
 *
 *  - /logout is meaningless for stateless JWT — the token lives in the client.
 *    Keycloak token revocation (if needed) must be done by calling Keycloak's
 *    end_session endpoint directly, which is the gateway's / client's responsibility.
 *
 *  - /health is already exposed at /actuator/health (Spring Boot Actuator).
 *    A duplicate in user-service adds noise with no value.
 *
 * Reachable via gateway at: GET /api/v1/auth/me
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Current user information")
@Slf4j
public class AuthenticationController {

    @GetMapping("/me")
    @Operation(summary = "Current user info",
               description = "Returns the username and granted roles from the JWT the gateway forwarded.")
    @ApiResponse(responseCode = "200", description = "User info returned")
    @ApiResponse(responseCode = "401", description = "No valid token")
    public Mono<ResponseEntity<Map<String, Object>>> me() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(auth -> {
                    log.debug("Current user: {}", auth.getName());
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "username",    auth.getName(),
                            "roles",       auth.getAuthorities(),
                            "authenticated", auth.isAuthenticated()
                    ));
                })
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "No active session")));
    }
}
