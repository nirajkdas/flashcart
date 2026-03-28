-- ============================================================
-- V2__seed_data.sql
-- FlashCart sample data
-- ============================================================

-- Admin user  (password: Admin@123 — bcrypt)
INSERT INTO users (username, email, password_hash, full_name, role) VALUES
('admin',   'admin@flashcart.com',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lh7y', 'System Admin',   'ADMIN'),
('seller1', 'seller@flashcart.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lh7y', 'TechGear Store',  'SELLER'),
('alice',   'alice@example.com',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lh7y', 'Alice Johnson',   'CUSTOMER'),
('bob',     'bob@example.com',      '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lh7y', 'Bob Smith',       'CUSTOMER');

-- Categories
INSERT INTO categories (name, slug, description) VALUES
('Electronics',   'electronics',   'Gadgets, phones, laptops and accessories'),
('Fashion',       'fashion',       'Clothing, shoes and accessories'),
('Home & Kitchen','home-kitchen',  'Furniture, appliances and cookware'),
('Sports',        'sports',        'Fitness, outdoor and sports gear'),
('Books',         'books',         'Printed and digital books');

-- Products (seller_id = 2 = seller1)
INSERT INTO products (seller_id, category_id, name, slug, description, base_price, stock_quantity) VALUES
(2, 1, 'Wireless Noise-Cancelling Headphones', 'wireless-nc-headphones',
 'Premium over-ear headphones with 30h battery life and active noise cancellation', 199.99, 500),
(2, 1, 'Mechanical Gaming Keyboard',           'mechanical-gaming-keyboard',
 'RGB backlit mechanical keyboard with Cherry MX Red switches',                   129.99, 300),
(2, 1, 'Portable SSD 1TB',                    'portable-ssd-1tb',
 'Ultra-fast 1TB portable SSD — read up to 1050MB/s',                            109.99, 400),
(2, 1, 'Smartwatch Pro X',                    'smartwatch-pro-x',
 'Fitness smartwatch with GPS, heart rate monitor and 7-day battery',             249.99, 200),
(2, 2, 'Running Shoes UltraBoost',             'running-shoes-ultraboost',
 'Lightweight, responsive running shoes with Boost cushioning technology',         119.99, 600),
(2, 3, 'Stainless Steel Air Fryer 5.5L',      'air-fryer-5-5l',
 'Digital air fryer with 8 preset programs and non-stick basket',                  89.99, 350);

-- A flash sale starting 1 minute from now (for dev testing; adjust timestamps as needed)
INSERT INTO flash_sales (name, description, start_time, end_time, status, created_by) VALUES
('Lightning Deals — Tech Week',
 'Massive discounts on top electronics for 2 hours only!',
 NOW() + INTERVAL '1 minute',
 NOW() + INTERVAL '121 minutes',
 'SCHEDULED',
 1);

-- Flash sale items
INSERT INTO flash_sale_items (flash_sale_id, product_id, sale_price, allocated_quantity, max_per_user)
SELECT fs.id, p.id,
       CASE p.slug
           WHEN 'wireless-nc-headphones'  THEN 99.99
           WHEN 'mechanical-gaming-keyboard' THEN 69.99
           WHEN 'portable-ssd-1tb'        THEN 59.99
           WHEN 'smartwatch-pro-x'        THEN 149.99
       END,
       CASE p.slug
           WHEN 'wireless-nc-headphones'  THEN 50
           WHEN 'mechanical-gaming-keyboard' THEN 30
           WHEN 'portable-ssd-1tb'        THEN 40
           WHEN 'smartwatch-pro-x'        THEN 20
       END,
       2
FROM flash_sales fs, products p
WHERE fs.name = 'Lightning Deals — Tech Week'
  AND p.slug IN ('wireless-nc-headphones','mechanical-gaming-keyboard','portable-ssd-1tb','smartwatch-pro-x');
