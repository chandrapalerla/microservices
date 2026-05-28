-- V3: Seed the 8 root categories used across the e-commerce platform
INSERT INTO categories (name, description, slug, active) VALUES
    ('Electronics',   'Electronic devices, gadgets and accessories',  'electronics',  TRUE),
    ('Clothing',      'Apparel for men, women and children',          'clothing',     TRUE),
    ('Books',         'Physical books, e-books and audio books',      'books',        TRUE),
    ('Home & Garden', 'Home décor, kitchen, tools and garden items',  'home-garden',  TRUE),
    ('Sports',        'Sports equipment and outdoor gear',            'sports',       TRUE),
    ('Beauty',        'Beauty, health and personal care products',    'beauty',       TRUE),
    ('Toys',          'Toys, games and hobbies for all ages',         'toys',         TRUE),
    ('Food',          'Grocery, gourmet food and beverages',          'food',         TRUE);
