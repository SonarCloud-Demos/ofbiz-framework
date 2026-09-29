#!/bin/sh
set -eu

base_url="${MODERN_BASE_URL:-http://localhost:8080}"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT HUP INT TERM

curl --fail --silent --show-error "$base_url/modern/platform" > "$work_dir/modern.html"
curl --fail --silent --show-error "$base_url/modern/catalog" > "$work_dir/catalog.html"
curl --fail --silent --show-error "$base_url/route-manifest.json" > "$work_dir/routes.json"
curl --fail --silent --show-error --dump-header "$work_dir/login.headers" --output /dev/null "$base_url/auth/login?return=/modern/platform"
curl --fail --silent --show-error "$base_url/legacy/webtools/control/main" > "$work_dir/legacy.html"
curl --fail --silent --show-error http://127.0.0.1:10000/health > "$work_dir/storage.json"
curl --fail --silent --show-error --user modern:local-messaging-only http://127.0.0.1:15672/api/health/checks/alarms > "$work_dir/messaging.json"

catalog_unauth_status="$(curl --silent --show-error --output "$work_dir/catalog-unauth.json" \
  --write-out '%{http_code}' "$base_url/bff/catalog/categories/CATALOG1")"
test "$catalog_unauth_status" = 401

cookie_file="$work_dir/cookies"
curl --fail --silent --show-error --location --cookie-jar "$cookie_file" --cookie "$cookie_file" \
  "$base_url/auth/login?return=/modern/catalog" > "$work_dir/keycloak-login.html"
login_action="$(sed -n 's/.*<form id="kc-form-login".* action="\([^"]*\)".*/\1/p' \
  "$work_dir/keycloak-login.html" | sed 's/&amp;/\&/g')"
test -n "$login_action"
curl --fail --silent --show-error --location --cookie-jar "$cookie_file" --cookie "$cookie_file" \
  --data 'username=developer' --data 'password=local-development-only' --data 'credentialId=' \
  "$login_action" > "$work_dir/catalog-authenticated.html"
curl --fail --silent --show-error --cookie "$cookie_file" \
  "$base_url/bff/catalog/categories/CATALOG1" > "$work_dir/catalog-root.json"
curl --fail --silent --show-error --cookie "$cookie_file" \
  "$base_url/bff/catalog/categories/100/products?limit=3&sort=catalog" > "$work_dir/catalog-browse.json"
curl --fail --silent --show-error --cookie "$cookie_file" \
  "$base_url/bff/catalog/search?q=gizmo&limit=3&sort=name" > "$work_dir/catalog-search.json"

grep -q 'app.js' "$work_dir/modern.html"
grep -q 'app.js' "$work_dir/catalog.html"
grep -q 'Modern experience' "$work_dir/routes.json"
grep -q '"path": "/modern/catalog"' "$work_dir/routes.json"
grep -q '"experience": "candidate"' "$work_dir/routes.json"
grep -qi 'location: http://localhost:8180/.*/auth' "$work_dir/login.headers"
grep -q '"categoryId":"CATALOG1"' "$work_dir/catalog-root.json"
grep -q '"productId"' "$work_dir/catalog-browse.json"
grep -q 'Gizmo' "$work_dir/catalog-search.json"
grep -q 'local-blob-port' "$work_dir/storage.json"
grep -q 'ok' "$work_dir/messaging.json"
if docker compose -f "$(dirname "$0")/compose.yaml" exec -T \
  -e PGPASSWORD=local-platform-only postgres \
  psql --host=127.0.0.1 --username=platform_owner --dbname=isolation_probe --command='select 1' >/dev/null 2>&1; then
  echo 'Database isolation failed: platform_owner connected to isolation_probe' >&2
  exit 1
fi
if grep -q 'data-experience="modern"' "$work_dir/legacy.html"; then
  echo 'Legacy route incorrectly contains the modern marker' >&2
  exit 1
fi
echo "Hybrid smoke test passed: $base_url"
