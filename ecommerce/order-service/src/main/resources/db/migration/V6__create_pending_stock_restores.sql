-- V6: Pending stock restores — compensation records written when restoreStock() circuit breaker
--     is open. StockCompensationScheduler retries until product-service recovers.
CREATE TABLE pending_stock_restores (
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id     BIGINT        NOT NULL,
    product_id   BIGINT        NOT NULL,
    quantity     INT           NOT NULL,
    reason       VARCHAR(200),
    status       VARCHAR(10)   NOT NULL DEFAULT 'PENDING'
                               COMMENT 'PENDING | DONE | FAILED',
    retry_count  INT           NOT NULL DEFAULT 0,
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP     NULL,
    INDEX idx_psr_status   (status),
    INDEX idx_psr_order_id (order_id)
);
