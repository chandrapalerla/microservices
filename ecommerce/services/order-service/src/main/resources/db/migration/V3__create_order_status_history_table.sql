-- V3: Order status history — full audit trail of every state transition
CREATE TABLE order_status_history (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id    BIGINT       NOT NULL,
    from_status VARCHAR(30),
    to_status   VARCHAR(30)  NOT NULL,
    reason      VARCHAR(500),
    changed_by  VARCHAR(255) NOT NULL,
    changed_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_history_order FOREIGN KEY (order_id) REFERENCES orders(id)
);
