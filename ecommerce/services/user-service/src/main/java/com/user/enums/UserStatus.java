package com.user.enums;

/**
 * Lifecycle status of a user account.
 *
 * ACTIVE    — normal account, can log in and place orders
 * INACTIVE  — account disabled by the user (self-deactivation)
 * SUSPENDED — account suspended by an admin
 *
 * Stored as VARCHAR via @Enumerated(EnumType.STRING).
 */
public enum UserStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED
}
