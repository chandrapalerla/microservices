-- =============================================================================
-- V1 — Create users table
--
-- Matches com.user.entity.User exactly.
-- Hibernate ddl-auto=validate will verify every column and constraint on startup.
--
-- Columns:
--   id          — PK, auto-increment
--   version     — optimistic-lock counter (@Version — Hibernate manages)
--   name        — full display name
--   email       — unique; used as login identity and by order-service
--   phone       — optional contact number
--   role        — USER | ADMIN  (stored as VARCHAR, @Enumerated STRING)
--   status      — ACTIVE | INACTIVE | SUSPENDED
--   created_at  — set once on INSERT via @PrePersist
--   updated_at  — refreshed on every UPDATE via @PreUpdate
-- =============================================================================

CREATE TABLE users (
    id         BIGINT          NOT NULL AUTO_INCREMENT,
    version    BIGINT          NOT NULL DEFAULT 0,
    name       VARCHAR(200)    NOT NULL,
    email      VARCHAR(255)    NOT NULL,
    phone      VARCHAR(20),
    role       VARCHAR(20)     NOT NULL DEFAULT 'USER',
    status     VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME        NOT NULL,
    updated_at DATETIME,

    CONSTRAINT pk_users        PRIMARY KEY (id),
    CONSTRAINT uk_users_email  UNIQUE      (email)
);
