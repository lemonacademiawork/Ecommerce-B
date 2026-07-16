-- Create table for product images collection
CREATE TABLE product_images (
    product_id UUID NOT NULL,
    image_url VARCHAR(255) NOT NULL,
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

-- Migrate existing imageUrl data to product_images table
INSERT INTO product_images (product_id, image_url)
SELECT id, image_url FROM products WHERE image_url IS NOT NULL AND image_url != '';

-- Note: We are not dropping the image_url column from products table yet,
-- as it might be needed for a brief period during zero-downtime deployment rollback.
-- But it can be dropped in a future migration:
-- ALTER TABLE products DROP COLUMN image_url;
