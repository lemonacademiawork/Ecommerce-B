-- Create product_reviews table
CREATE TABLE product_reviews (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    user_id UUID NOT NULL,
    rating INT NOT NULL,
    comment VARCHAR(1000),
    photo_url1 VARCHAR(255),
    photo_url2 VARCHAR(255),
    verified_purchase BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_product_reviews_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_product_reviews_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_product_reviews_user_product UNIQUE (user_id, product_id)
);

-- Add indexes for review queries and aggregations
CREATE INDEX idx_product_reviews_product_id ON product_reviews(product_id);
CREATE INDEX idx_product_reviews_user_id ON product_reviews(user_id);
CREATE INDEX idx_product_reviews_created_at ON product_reviews(created_at);
CREATE INDEX idx_product_reviews_rating ON product_reviews(rating);
