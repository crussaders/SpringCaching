-- ============================================================
-- V2 – Seed Data
-- ============================================================

-- Categories
INSERT INTO categories (id, name, description)
VALUES (category_seq.NEXTVAL, 'Electronics',  'Phones, laptops, tablets and accessories');

INSERT INTO categories (id, name, description)
VALUES (category_seq.NEXTVAL, 'Books',        'Fiction, non-fiction and technical books');

INSERT INTO categories (id, name, description)
VALUES (category_seq.NEXTVAL, 'Clothing',     'Men''s and women''s apparel');

INSERT INTO categories (id, name, description)
VALUES (category_seq.NEXTVAL, 'Home & Garden','Furniture, tools, plants and outdoor equipment');

-- Products – Electronics (category_id = 1)
INSERT INTO products (id, name, description, price, stock_quantity, category_id)
VALUES (product_seq.NEXTVAL, 'Laptop Pro 15',
        '15-inch laptop with 16GB RAM and 512GB SSD',
        1299.99, 50, 1);

INSERT INTO products (id, name, description, price, stock_quantity, category_id)
VALUES (product_seq.NEXTVAL, 'Wireless Headphones',
        'Noise-cancelling over-ear headphones',
        199.99, 100, 1);

INSERT INTO products (id, name, description, price, stock_quantity, category_id)
VALUES (product_seq.NEXTVAL, 'Smartphone X12',
        'Flagship smartphone with 256GB storage',
        999.99, 75, 1);

INSERT INTO products (id, name, description, price, stock_quantity, category_id)
VALUES (product_seq.NEXTVAL, 'USB-C Hub',
        '7-in-1 USB-C hub with HDMI and card reader',
        49.99, 200, 1);

-- Products – Books (category_id = 2)
INSERT INTO products (id, name, description, price, stock_quantity, category_id)
VALUES (product_seq.NEXTVAL, 'Clean Code',
        'A handbook of agile software craftsmanship by Robert C. Martin',
        34.99, 150, 2);

INSERT INTO products (id, name, description, price, stock_quantity, category_id)
VALUES (product_seq.NEXTVAL, 'Spring in Action',
        'Covers Spring Boot 3, Spring MVC, Spring Data, and more',
        39.99, 80, 2);

INSERT INTO products (id, name, description, price, stock_quantity, category_id)
VALUES (product_seq.NEXTVAL, 'Designing Data-Intensive Applications',
        'The big ideas behind reliable, scalable, and maintainable systems',
        44.99, 60, 2);

-- Products – Clothing (category_id = 3)
INSERT INTO products (id, name, description, price, stock_quantity, category_id)
VALUES (product_seq.NEXTVAL, 'Classic T-Shirt',
        '100% organic cotton, available in multiple colours',
        19.99, 500, 3);

INSERT INTO products (id, name, description, price, stock_quantity, category_id)
VALUES (product_seq.NEXTVAL, 'Slim-Fit Jeans',
        'Stretch denim, modern slim fit',
        59.99, 250, 3);

-- Products – Home & Garden (category_id = 4)
INSERT INTO products (id, name, description, price, stock_quantity, category_id)
VALUES (product_seq.NEXTVAL, 'Ergonomic Office Chair',
        'Lumbar support, adjustable armrests, 5-year warranty',
        349.99, 30, 4);

INSERT INTO products (id, name, description, price, stock_quantity, category_id)
VALUES (product_seq.NEXTVAL, 'Indoor Plant Collection',
        'Set of 5 low-maintenance indoor plants',
        49.99, 120, 4);
