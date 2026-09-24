CREATE TABLE catalog_product (
    product_id VARCHAR(64) PRIMARY KEY,
    internal_name VARCHAR(255),
    product_name VARCHAR(255),
    product_type_id VARCHAR(64),
    status_id VARCHAR(64),
    description VARCHAR(2000),
    source_updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    projected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source_checksum VARCHAR(64) NOT NULL
);
CREATE INDEX catalog_product_search_idx
    ON catalog_product (LOWER(product_name), LOWER(internal_name), product_id);

CREATE TABLE catalog_projection_checkpoint (
    source_name VARCHAR(64) PRIMARY KEY,
    source_cursor VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
