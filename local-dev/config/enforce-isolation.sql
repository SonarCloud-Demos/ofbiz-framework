SELECT 'CREATE ROLE product_catalog LOGIN PASSWORD ''local-product-catalog-only'''
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'product_catalog')\gexec
ALTER ROLE product_catalog WITH LOGIN PASSWORD 'local-product-catalog-only';

SELECT 'CREATE DATABASE product_catalog OWNER product_catalog'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'product_catalog')\gexec

REVOKE CONNECT ON DATABASE platform FROM PUBLIC;
REVOKE CONNECT ON DATABASE isolation_probe FROM PUBLIC;
REVOKE CONNECT ON DATABASE product_catalog FROM PUBLIC;
GRANT CONNECT ON DATABASE platform TO platform_owner;
GRANT CONNECT ON DATABASE isolation_probe TO isolation_probe;
GRANT CONNECT ON DATABASE product_catalog TO product_catalog;
