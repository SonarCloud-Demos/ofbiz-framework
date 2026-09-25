CREATE TABLE catalog_category (
    category_id VARCHAR(64) PRIMARY KEY,
    parent_category_id VARCHAR(64),
    name VARCHAR(255) NOT NULL,
    description VARCHAR(4000),
    image_url VARCHAR(2048),
    sequence_num NUMERIC(20, 6),
    from_date TIMESTAMP WITH TIME ZONE,
    thru_date TIMESTAMP WITH TIME ZONE,
    source_checksum VARCHAR(64) NOT NULL,
    projected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX catalog_category_parent_idx
    ON catalog_category (parent_category_id, sequence_num, category_id);

CREATE TABLE catalog_product (
    product_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(4000),
    image_url VARCHAR(2048),
    product_type VARCHAR(32) NOT NULL,
    is_virtual BOOLEAN NOT NULL DEFAULT FALSE,
    is_variant BOOLEAN NOT NULL DEFAULT FALSE,
    source_checksum VARCHAR(64) NOT NULL,
    projected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX catalog_product_search_idx ON catalog_product (LOWER(name), product_id);

CREATE TABLE catalog_category_product (
    category_id VARCHAR(64) NOT NULL REFERENCES catalog_category(category_id),
    product_id VARCHAR(64) NOT NULL REFERENCES catalog_product(product_id),
    sequence_num NUMERIC(20, 6),
    from_date TIMESTAMP WITH TIME ZONE,
    thru_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (category_id, product_id, from_date)
);

CREATE INDEX catalog_category_product_order_idx
    ON catalog_category_product (category_id, sequence_num, product_id);

CREATE TABLE catalog_projection_checkpoint (
    source_name VARCHAR(64) PRIMARY KEY,
    source_cursor VARCHAR(512) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
