-- V2: Order items — line items belonging to an order (snapshot of product at order time)
CREATE TABLE order_items (
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id     BIGINT        NOT NULL,
    product_id   BIGINT        NOT NULL,
    product_name VARCHAR(255)  NOT NULL,
    product_sku  VARCHAR(100),
    quantity     INT           NOT NULL,
    unit_price   DECIMAL(10,2) NOT NULL,
    total_price  DECIMAL(10,2) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id)
);
