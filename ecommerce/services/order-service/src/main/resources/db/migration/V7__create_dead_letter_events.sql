-- V7: Dead letter queue — stores Kafka publish failures so events are never silently lost.
--     Ops can inspect and replay these records after broker recovery.
CREATE TABLE dead_letter_events (
    id            BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id      VARCHAR(36)   NOT NULL  COMMENT 'UUID of the original OrderEvent',
    event_type    VARCHAR(50)   NOT NULL,
    order_id      BIGINT        NOT NULL,
    payload       TEXT          NOT NULL  COMMENT 'Full JSON of the failed OrderEvent',
    error_message VARCHAR(500),
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_dlq_order_id (order_id),
    INDEX idx_dlq_created  (created_at)
);
