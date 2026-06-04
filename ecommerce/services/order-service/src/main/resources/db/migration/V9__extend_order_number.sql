-- V9: Extend order_number column to accommodate the TMP-<UUID> placeholder
--     used during order creation before the sequential number is assigned.
--     UUID is 36 chars + "TMP-" prefix = 40 chars; final format ORD-YYYY-NNNNN = 14 chars.
ALTER TABLE orders MODIFY COLUMN order_number VARCHAR(50) NOT NULL;
