package com.user.entity;

import com.user.enums.UserRole;
import com.user.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * JPA entity mapped to the `users` table.
 *
 * Schema is owned by Flyway (V1__create_users_table.sql).
 * Hibernate ddl-auto=validate confirms column/constraint alignment at startup.
 *
 * Optimistic locking: @Version on `version` — concurrent updates detected
 * automatically; ObjectOptimisticLockingFailureException → HTTP 409.
 *
 * Timestamps: @PrePersist sets createdAt + updatedAt on first save;
 *             @PreUpdate refreshes updatedAt on every subsequent save.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Optimistic lock — Hibernate increments this on every UPDATE. */
    @Version
    private Long version;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** Optional contact number. */
    @Column(length = 20)
    private String phone;

    /** USER or ADMIN — stored as VARCHAR, default USER. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.USER;

    /** ACTIVE / INACTIVE / SUSPENDED — stored as VARCHAR, default ACTIVE. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    /** Set once on first save; never modified afterwards. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Refreshed on every save after the first. */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── JPA lifecycle hooks ───────────────────────────────────────────────────

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (role   == null) role   = UserRole.USER;
        if (status == null) status = UserStatus.ACTIVE;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
