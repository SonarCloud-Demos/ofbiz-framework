#!/bin/sh
set -eu
shell_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

for file in index.html app.js route-manifest.js styles.css; do
    test -f "$shell_dir/src/$file"
done
rg -q 'data-generation' "$shell_dir/src/index.html"
rg -q 'Object\.freeze' "$shell_dir/src/route-manifest.js"
rg -q 'Route configuration error' "$shell_dir/src/app.js"
rg -q 'fetch\("/bff/session"' "$shell_dir/src/app.js"
rg -q 'fetch\("/bff/logout"' "$shell_dir/src/app.js"
rg -q 'role="alert"' "$shell_dir/src/index.html"
rg -q 'Product catalog' "$shell_dir/src/index.html"
rg -q '/bff/catalog/products' "$shell_dir/src/app.js"
rg -q 'generation: "hybrid"' "$shell_dir/src/route-manifest.js"
rg -q "frame-ancestors 'none'" "$shell_dir/nginx.conf"
