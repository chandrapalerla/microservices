-- V2: Products — core catalogue with inventory tracking and optimistic locking
CREATE TABLE products (
    id                   BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name                 VARCHAR(255)  NOT NULL,
    sku                  VARCHAR(100)  NOT NULL UNIQUE,
    description          TEXT,
    brand                VARCHAR(100),
    category_id          BIGINT,
    price                DECIMAL(10,2) NOT NULL,
    original_price       DECIMAL(10,2),
    stock_quantity       INT           NOT NULL DEFAULT 0,
    reserved_quantity    INT           NOT NULL DEFAULT 0,
    low_stock_threshold  INT           NOT NULL DEFAULT 10,
    status               VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    thumbnail_url        VARCHAR(500),
    weight               DECIMAL(8,3),
    version              BIGINT        NOT NULL DEFAULT 0,
    created_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                       ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES categories(id)
);
