package com.apigateway.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Gateway-level auth endpoints.
 *
 * WHY the token/refresh calls go through the gateway and NOT directly to Keycloak:
 *   The browser SPA lives on a different origin (localhost:5173) than Keycloak
 *   (localhost:30080).  Browsers block cross-origin form-encoded POSTs to the
 *   Keycloak token endpoint unless Keycloak's "Web Origins" is explicitly
 *   configured.  Routing through the gateway (which the SPA already trusts for
 *   all /api calls) is cleaner and keeps all Keycloak credentials server-side.
 *
 * POST /auth/token   — ROPC: exchange username+password for tokens
 * POST /auth/refresh — silent token refresh using a refresh_token
 * POST /auth/logout  — clear server session (stateless; best-effort revoke)
 * GET  /auth/health  — liveness check
 */
@RestController
@RequestMapping("/auth")
public class AuthenticationController {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationController.class);

    /** Keycloak token endpoint — injected from application.yaml */
    @Value("${spring.security.oauth2.client.provider.keycloak.token-uri}")
    private String keycloakTokenUri;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-secret}")
    private String clientSecret;

    private final RestClient restClient;

    public AuthenticationController(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    // ── Request/Response records ──────────────────────────────────────────────

    /** Login payload from the SPA */
    public record LoginRequest(String username, String password) {}

    /** Refresh-token payload from the SPA */
    public record RefreshRequest(String refreshToken) {}

    // ── POST /auth/token ──────────────────────────────────────────────────────

    /**
     * Proxies an ROPC (Resource Owner Password Credentials) request to Keycloak.
     *
     * The call is server-to-server so the browser never directly contacts Keycloak,
     * eliminating CORS entirely.
     *
     * Returns the raw Keycloak token response:
     *   { access_token, refresh_token, expires_in, token_type, ... }
     */
    @PostMapping("/token")
    public ResponseEntity<?> token(@RequestBody LoginRequest request) {
        try {
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type",    "password");
            body.add("client_id",     clientId);
            body.add("client_secret", clientSecret);
            body.add("username",      request.username());
            body.add("password",      request.password());
            body.add("scope",         "openid profile email");

            @SuppressWarnings("unchecked")
            Map<String, Object> keycloakResponse = restClient.post()
                    .uri(keycloakTokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            logger.info("Token issued for user: {}", request.username());
            return ResponseEntity.ok(keycloakResponse);

        } catch (HttpClientErrorException.Unauthorized e) {
            logger.warn("Login failed for user: {} — invalid credentials", request.username());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "invalid_credentials",
                                 "message", "Invalid username or password"));

        } catch (HttpClientErrorException e) {
            logger.warn("Keycloak error during login: {}", e.getStatusCode());
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("error", "keycloak_error",
                                 "message", e.getMessage()));

        } catch (Exception e) {
            logger.error("Unexpected error during token exchange", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "auth_unavailable",
                                 "message", "Authentication service is unavailable"));
        }
    }

    // ── POST /auth/refresh ────────────────────────────────────────────────────

    /**
     * Silently exchanges a refresh token for a new access token.
     * Called by the Axios interceptor on 401 — the SPA never calls Keycloak directly.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest request) {
        try {
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type",    "refresh_token");
            body.add("client_id",     clientId);
            body.add("client_secret", clientSecret);
            body.add("refresh_token", request.refreshToken());

            @SuppressWarnings("unchecked")
            Map<String, Object> keycloakResponse = restClient.post()
                    .uri(keycloakTokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            logger.debug("Token refreshed successfully");
            return ResponseEntity.ok(keycloakResponse);

        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.BadRequest e) {
            // Expired or invalid refresh token — client must re-authenticate
            logger.info("Refresh token rejected: {}", e.getStatusCode());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "refresh_token_expired",
                                 "message", "Session expired. Please log in again."));

        } catch (Exception e) {
            logger.error("Unexpected error during token refresh", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "auth_unavailable",
                                 "message", "Authentication service is unavailable"));
        }
    }

    // ── POST /auth/logout ─────────────────────────────────────────────────────

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            if (auth != null) {
                SecurityContextHolder.clearContext();
                request.getSession().invalidate();
                logger.info("User {} logged out", auth.getName());
                return ResponseEntity.ok(Map.of("status", "success", "message", "Logged out"));
            }

            // Accept logout even without a server-side session (stateless JWT)
            return ResponseEntity.ok(Map.of("status", "success", "message", "Logged out"));

        } catch (Exception e) {
            logger.error("Error during logout", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // ── GET /auth/health ──────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "API Gateway"));
    }
}
