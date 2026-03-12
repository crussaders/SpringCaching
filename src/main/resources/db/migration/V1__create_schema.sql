-- ============================================================
-- V1 – Initial Schema
-- Compatible with both H2 (MODE=Oracle) and Oracle Database
-- ============================================================

-- Sequences (Oracle syntax; H2 MODE=Oracle supports these)
CREATE SEQUENCE category_seq START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE product_seq  START WITH 1 INCREMENT BY 1 NOCACHE;

-- Categories table
CREATE TABLE categories (
    id          NUMBER(19)    NOT NULL,
    name        VARCHAR2(100) NOT NULL,
    description VARCHAR2(500),
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT uq_categories_name UNIQUE (name)
);

-- Products table
CREATE TABLE products (
    id             NUMBER(19)     NOT NULL,
    name           VARCHAR2(200)  NOT NULL,
    description    VARCHAR2(1000),
    price          NUMBER(10, 2)  NOT NULL,
    stock_quantity NUMBER(10)     DEFAULT 0 NOT NULL,
    category_id    NUMBER(19),
    created_at     TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_products     PRIMARY KEY (id),
    CONSTRAINT fk_products_cat FOREIGN KEY (category_id) REFERENCES categories(id)
);

-- Indexes for performance
CREATE INDEX idx_products_category ON products (category_id);
CREATE INDEX idx_products_name     ON products (name);
