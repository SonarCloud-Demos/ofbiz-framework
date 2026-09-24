#!/bin/sh
set -eu
repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
catalog_url=${MODERN_CATALOG_URL:-http://localhost:18083}
curl --fail --silent --show-error -X POST \
    -H 'Content-Type: application/json' \
    -H 'X-Catalog-Ingestion-Key: local-catalog-ingestion' \
    -H 'X-Catalog-Source-Cursor: local-seed|PHASE3-DEMO-1' \
    --data-binary "@$repo_root/local-dev/seed/catalog-products.json" \
    "$catalog_url/internal/v1/catalog/projection/products"
printf '\nCatalog projection seed loaded.\n'
