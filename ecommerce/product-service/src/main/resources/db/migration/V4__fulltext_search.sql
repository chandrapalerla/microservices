-- V4: Full-text search index on product name, description, and brand.
-- Enables MATCH(...) AGAINST (...) queries that can leverage the index,
-- unlike LIKE '%term%' which always does a full table scan.
ALTER TABLE products ADD FULLTEXT INDEX ft_product_search (name, description, brand);
