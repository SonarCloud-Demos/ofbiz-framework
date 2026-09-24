#!/bin/sh
set -eu
repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$repo_root"

for path in services/product-catalog-service platform/contracts/http/product-catalog-v1.yaml \
    docs/migration/phase-3/README.md docs/migration/phase-3/exit-gate.md; do
    test -e "$path"
done
rg -q 'generation: hybrid' platform/routes.yaml
rg -q 'hasRole\("CATALOG"\)' services/shell-bff/src/main/java/org/apache/ofbiz/modern/shell/SecurityConfiguration.java
rg -q 'CREATE TABLE catalog_product' services/product-catalog-service/src/main/resources/db/migration/V1__catalog_projection.sql
rg -q 'X-Catalog-Ingestion-Key' services/product-catalog-service/src/main/java/org/apache/ofbiz/modern/catalog/CatalogController.java
rg -q 'catalog-postgres:' local-dev/docker-compose.yml
rg -q 'product-catalog-service:' local-dev/docker-compose.yml
rg -q 'OFBiz remains authoritative' web/shell/src/index.html
rg -q 'modernProductProjectionExport' applications/product/webapp/catalog/WEB-INF/controller.xml
rg -q 'snapshotProductIds' applications/product/src/main/java/org/apache/ofbiz/product/product/ModernCatalogProjectionEvents.java
rg -q -F 'location = /catalog/control/modernProductProjectionExport { return 404; }' local-dev/nginx-edge.conf
rg -q 'azurerm_container_app" "product_catalog' infra/modules/platform/main.tf
test -x local-dev/seed-catalog.sh

# A projection service may not gain a path to the monolith's database or code.
if rg -n '(jdbc:.*ofbiz|import org\.apache\.ofbiz\.(?!modern\.))' services/product-catalog-service/src/main -P; then
    echo 'catalog pilot directly depends on OFBiz' >&2
    exit 1
fi
