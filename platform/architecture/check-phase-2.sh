#!/bin/sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$repo_root"

for generation in legacy modern; do
    rg -q "generation: $generation" platform/routes.yaml
done

# Secure defaults: unknown routes stay legacy, inbound classification is
# removed, and every explicit modern target receives an edge-owned header.
rg -q '^defaultOwner: legacy-ofbiz$' platform/routes.yaml
rg -q 'proxy_hide_header X-Application-Generation' local-dev/nginx-edge.conf
rg -q 'proxy_set_header X-Application-Generation ""' local-dev/nginx-edge.conf
rg -q 'proxy_set_header X-Forwarded-For \$remote_addr' local-dev/nginx-edge.conf
rg -q 'location /modern/' local-dev/nginx-edge.conf
rg -q 'add_header X-Application-Generation "modern" always' local-dev/nginx-edge.conf
rg -q -F 'location / {' local-dev/nginx-edge.conf
rg -q 'add_header X-Application-Generation "legacy" always' local-dev/nginx-edge.conf

for path in \
    services/shell-bff/build.gradle \
    services/shell-bff/src/main/java/org/apache/ofbiz/modern/shell/SecurityConfiguration.java \
    services/shell-bff/src/main/java/org/apache/ofbiz/modern/shell/TenantValidatedOidcUserService.java \
    services/shell-bff/src/main/java/org/apache/ofbiz/modern/shell/LegacyAssertionController.java; do
    test -f "$path"
done
rg -q 'authentication: entra-bff-session' platform/routes.yaml
rg -q 'withPkce' services/shell-bff/src/main/java/org/apache/ofbiz/modern/shell/SecurityConfiguration.java
rg -q 'CookieCsrfTokenRepository' services/shell-bff/src/main/java/org/apache/ofbiz/modern/shell/SecurityConfiguration.java
rg -q 'hasRole\("LEGACY_USER"\)' services/shell-bff/src/main/java/org/apache/ofbiz/modern/shell/SecurityConfiguration.java
rg -q 'allowedTenants.contains' services/shell-bff/src/main/java/org/apache/ofbiz/modern/shell/TenantValidatedOidcUserService.java
rg -q 'Duration.ofSeconds\(60\)' services/shell-bff/src/main/java/org/apache/ofbiz/modern/shell/LegacyAssertionController.java
rg -q 'internal;' local-dev/nginx-edge.conf
rg -q 'auth_request /__auth/legacy' local-dev/nginx-edge.conf
rg -q 'proxy_set_header X-Legacy-Identity-Assertion \$legacy_assertion' local-dev/nginx-edge.conf
