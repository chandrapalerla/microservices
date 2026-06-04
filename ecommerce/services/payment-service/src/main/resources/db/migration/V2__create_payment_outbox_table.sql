CREATE TABLE payment_outbox (
    id           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    payment_id   BIGINT      NOT NULL,
    order_id     BIGINT      NOT NULL,
    event_type   VARCHAR(50) NOT NULL,
    payload      TEXT,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count  INT         NOT NULL DEFAULT 0,
    created_at   DATETIME(6) NOT NULL,
    processed_at DATETIME(6),
    INDEX idx_payment_outbox_status     (status),
    INDEX idx_payment_outbox_payment_id (payment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
