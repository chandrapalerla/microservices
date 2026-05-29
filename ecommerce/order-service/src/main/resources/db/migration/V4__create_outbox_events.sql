-- V4: Transactional Outbox — events written in the same DB transaction as order state changes,
--     then relayed to Kafka by OutboxPoller. Guarantees at-least-once Kafka delivery.
CREATE TABLE outbox_events (
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id     BIGINT        NOT NULL,
    event_type   VARCHAR(50)   NOT NULL,
    reason       VARCHAR(500),
    status       VARCHAR(10)   NOT NULL DEFAULT 'PENDING'
                               COMMENT 'PENDING | SENT | FAILED',
    retry_count  INT           NOT NULL DEFAULT 0,
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP     NULL,
    INDEX idx_outbox_status    (status),
    INDEX idx_outbox_order_id  (order_id)
);
