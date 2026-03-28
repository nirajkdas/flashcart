-- ============================================================
-- V1__init_schema.sql
-- FlashCart core schema
-- ============================================================

-- USERS
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    email           VARCHAR(100) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(100) NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'CUSTOMER',
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_role CHECK (role IN ('CUSTOMER','SELLER','ADMIN'))
);

-- CATEGORIES
CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    slug        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- PRODUCTS
CREATE TABLE products (
    id                  BIGSERIAL PRIMARY KEY,
    seller_id           BIGINT NOT NULL REFERENCES users(id),
    category_id         BIGINT REFERENCES categories(id) ON DELETE SET NULL,
    name                VARCHAR(255) NOT NULL,
    slug                VARCHAR(255) NOT NULL UNIQUE,
    description         TEXT,
    base_price          DECIMAL(12,2) NOT NULL,
    stock_quantity      INT NOT NULL DEFAULT 0,
    image_url           VARCHAR(500),
    is_active           BOOLEAN DEFAULT TRUE,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_base_price  CHECK (base_price >= 0),
    CONSTRAINT chk_stock       CHECK (stock_quantity >= 0)
);

-- FLASH SALES
CREATE TABLE flash_sales (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(255) NOT NULL,
    description         TEXT,
    start_time          TIMESTAMP NOT NULL,
    end_time            TIMESTAMP NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    created_by          BIGINT NOT NULL REFERENCES users(id),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_sale_status  CHECK (status IN ('SCHEDULED','ACTIVE','ENDED','CANCELLED')),
    CONSTRAINT chk_sale_times   CHECK (end_time > start_time)
);

-- FLASH SALE ITEMS (products in a flash sale with discounted price & limited qty)
CREATE TABLE flash_sale_items (
    id                  BIGSERIAL PRIMARY KEY,
    flash_sale_id       BIGINT NOT NULL REFERENCES flash_sales(id) ON DELETE CASCADE,
    product_id          BIGINT NOT NULL REFERENCES products(id),
    sale_price          DECIMAL(12,2) NOT NULL,
    allocated_quantity  INT NOT NULL,
    sold_quantity       INT NOT NULL DEFAULT 0,
    max_per_user        INT NOT NULL DEFAULT 1,
    version             BIGINT NOT NULL DEFAULT 0,  -- optimistic locking
    CONSTRAINT chk_sale_price   CHECK (sale_price >= 0),
    CONSTRAINT chk_alloc_qty    CHECK (allocated_quantity > 0),
    CONSTRAINT chk_sold_qty     CHECK (sold_quantity >= 0),
    CONSTRAINT uq_sale_product  UNIQUE (flash_sale_id, product_id)
);

-- ORDERS
CREATE TABLE orders (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_amount    DECIMAL(12,2) NOT NULL,
    shipping_address TEXT,
    notes           TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_order_status CHECK (status IN ('PENDING','CONFIRMED','SHIPPED','DELIVERED','CANCELLED','REFUNDED'))
);

-- ORDER ITEMS
CREATE TABLE order_items (
    id                  BIGSERIAL PRIMARY KEY,
    order_id            BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id          BIGINT NOT NULL REFERENCES products(id),
    flash_sale_item_id  BIGINT REFERENCES flash_sale_items(id),
    quantity            INT NOT NULL,
    unit_price          DECIMAL(12,2) NOT NULL,
    is_flash_sale_item  BOOLEAN DEFAULT FALSE,
    CONSTRAINT chk_quantity CHECK (quantity > 0)
);

-- CART ITEMS (temporary, stored in Redis but persisted here as fallback)
CREATE TABLE cart_items (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id      BIGINT NOT NULL REFERENCES products(id),
    quantity        INT NOT NULL DEFAULT 1,
    added_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_cart_qty CHECK (quantity > 0),
    CONSTRAINT uq_cart_user_product UNIQUE (user_id, product_id)
);

-- PRODUCT REVIEWS
CREATE TABLE product_reviews (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    product_id  BIGINT NOT NULL REFERENCES products(id),
    order_id    BIGINT REFERENCES orders(id),
    rating      INT NOT NULL,
    comment     TEXT,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_rating       CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT uq_user_product  UNIQUE (user_id, product_id)
);

-- NOTIFICATIONS (for broadcast events via WebSocket)
CREATE TABLE notifications (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES users(id) ON DELETE CASCADE,  -- NULL = broadcast
    type        VARCHAR(50) NOT NULL,
    title       VARCHAR(255) NOT NULL,
    message     TEXT NOT NULL,
    is_read     BOOLEAN DEFAULT FALSE,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- INDEXES
CREATE INDEX idx_products_seller     ON products(seller_id);
CREATE INDEX idx_products_category   ON products(category_id);
CREATE INDEX idx_products_slug       ON products(slug);
CREATE INDEX idx_flash_sales_status  ON flash_sales(status);
CREATE INDEX idx_flash_sales_times   ON flash_sales(start_time, end_time);
CREATE INDEX idx_orders_user         ON orders(user_id);
CREATE INDEX idx_orders_status       ON orders(status);
CREATE INDEX idx_order_items_order   ON order_items(order_id);
CREATE INDEX idx_notifications_user  ON notifications(user_id);
