package com.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Thin wrapper around the Keycloak Admin REST API, used solely for session revocation.
 *
 * The 'user-service-client' service account must have the 'manage-users' role
 * assigned under realm-management in Keycloak:
 *   Admin UI → Clients → user-service-client
 *     → Service Account Roles → realm-management → manage-users
 *
 * Two-step process for every revocation call:
 *   1. POST token endpoint (client_credentials) → short-lived admin token
 *   2. GET  /admin/realms/{realm}/users?email=X → resolve Keycloak user UUID
 *   3. POST /admin/realms/{realm}/users/{id}/logout → delete all sessions
 */
@Service
public class KeycloakAdminClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);

    @Value("${spring.security.oauth2.client.provider.keycloak.token-uri}")
    private String tokenUri;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-secret}")
    private String clientSecret;

    @Value("${keycloak.admin.server-url}")
    private String serverUrl;

    @Value("${keycloak.admin.realm}")
    private String realm;

    private final RestClient restClient = RestClient.create();

    public void revokeAllSessions(String email) {
        String token        = fetchAdminToken();
        String keycloakId   = findKeycloakUserId(email, token);
        postLogout(keycloakId, token);
        log.info("All Keycloak sessions revoked for {}", email);
    }

    @SuppressWarnings("unchecked")
    private String fetchAdminToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type",    "client_credentials");
        form.add("client_id",     clientId);
        form.add("client_secret", clientSecret);

        Map<String, Object> response = restClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("access_token")) {
            throw new IllegalStateException("Failed to obtain admin token from Keycloak");
        }
        return (String) response.get("access_token");
    }

    private String findKeycloakUserId(String email, String token) {
        String usersUrl = serverUrl + "/admin/realms/" + realm + "/users";

        List<Map<String, Object>> users = restClient.get()
                .uri(usersUrl + "?email={email}&exact=true", email)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (users == null || users.isEmpty()) {
            throw new IllegalStateException("No Keycloak account found for email: " + email);
        }
        return (String) users.get(0).get("id");
    }

    private void postLogout(String keycloakUserId, String token) {
        String logoutUrl = serverUrl + "/admin/realms/" + realm
                + "/users/" + keycloakUserId + "/logout";

        restClient.post()
                .uri(logoutUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity();
    }
}
