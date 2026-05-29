-- V8: Coupons — discount codes validated during order placement.
--     discount_type PERCENTAGE: discount = min(subtotal * value / 100, max_discount)
--     discount_type FIXED:      discount = min(value, subtotal)
CREATE TABLE coupons (
    id               BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code             VARCHAR(50)    NOT NULL,
    description      VARCHAR(200),
    discount_type    VARCHAR(15)    NOT NULL  COMMENT 'PERCENTAGE | FIXED',
    discount_value   DECIMAL(10,2)  NOT NULL,
    max_discount     DECIMAL(10,2)  NULL      COMMENT 'Cap for PERCENTAGE type; NULL = uncapped',
    min_order_amount DECIMAL(10,2)  NOT NULL DEFAULT 0.00,
    max_uses         INT            NOT NULL DEFAULT 1,
    times_used       INT            NOT NULL DEFAULT 0,
    valid_from       TIMESTAMP      NOT NULL,
    valid_until      TIMESTAMP      NOT NULL,
    active           BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_coupon_code (code),
    INDEX idx_coupon_active (active)
);
