package com.user.enums;

/**
 * Roles assignable to a user account.
 *
 * Stored as VARCHAR in the DB via @Enumerated(EnumType.STRING).
 * Mirrors the Keycloak realm roles USER / ADMIN — when a user authenticates
 * with Keycloak the JWT carries the same role string which is mapped to
 * ROLE_USER / ROLE_ADMIN by KeycloakJwtConverter.
 */
public enum UserRole {
    USER,
    ADMIN
}
