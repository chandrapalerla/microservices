-- V5: Idempotency keys — maps client-supplied X-Idempotency-Key → orderId so that
--     duplicate POST /orders requests return the original response without re-processing.
CREATE TABLE idempotency_keys (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    idempotency_key VARCHAR(100)  NOT NULL,
    order_id        BIGINT        NOT NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_idempotency_key (idempotency_key),
    INDEX idx_idem_order_id (order_id)
);
