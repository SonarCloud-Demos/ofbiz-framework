#!/bin/sh
set -eu

base_url="${MODERN_BASE_URL:-http://localhost:8080}"
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
compose_file="$script_dir/compose.yaml"
work_dir=$(mktemp -d)
restoration_complete=0

restore() {
  docker compose -f "$compose_file" start product-catalog >/dev/null 2>&1 || true
  if [ "$restoration_complete" -ne 1 ]; then
    CATALOG_ROUTE_DISABLED=false docker compose -f "$compose_file" up --detach --no-deps --force-recreate shell-bff >/dev/null 2>&1 || true
  fi
  rm -rf "$work_dir"
}
trap restore EXIT HUP INT TERM

wait_for_service() {
  service="$1"
  attempts=0
  while [ "$attempts" -lt 60 ]; do
    status=$(docker compose -f "$compose_file" ps --format json "$service" 2>/dev/null || true)
    if printf '%s' "$status" | grep -q '"Health":"healthy"'; then return 0; fi
    attempts=$((attempts + 1))
    sleep 1
  done
  echo "Timed out waiting for $service health" >&2
  return 1
}

authenticate() {
  cookie_file="$1"
  curl --fail --silent --show-error --location --cookie-jar "$cookie_file" --cookie "$cookie_file" \
    "$base_url/auth/login?return=/modern/catalog" > "$work_dir/keycloak-login.html"
  login_action=$(sed -n 's/.*<form id="kc-form-login".* action="\([^"]*\)".*/\1/p' \
    "$work_dir/keycloak-login.html" | sed 's/&amp;/\&/g')
  test -n "$login_action"
  curl --fail --silent --show-error --location --cookie-jar "$cookie_file" --cookie "$cookie_file" \
    --data 'username=developer' --data 'password=local-development-only' --data 'credentialId=' \
    "$login_action" > /dev/null
}

curl --fail --silent --show-error "$base_url/modern/catalog" > /dev/null
wait_for_service shell-bff
wait_for_service product-catalog

baseline_cookies="$work_dir/baseline-cookies"
authenticate "$baseline_cookies"
curl --fail --silent --show-error --cookie "$baseline_cookies" "$base_url/auth/session" \
  | grep -q '"catalogRouteEnabled":true'
curl --fail --silent --show-error --cookie "$baseline_cookies" \
  "$base_url/bff/catalog/categories/CATALOG1" > /dev/null

docker compose -f "$compose_file" stop product-catalog >/dev/null
failure_status=$(curl --silent --show-error --cookie "$baseline_cookies" --output "$work_dir/failure.json" \
  --write-out '%{http_code}' "$base_url/bff/catalog/categories/CATALOG1")
test "$failure_status" = 502
grep -q '"error":"upstream_unavailable"' "$work_dir/failure.json"
docker compose -f "$compose_file" start product-catalog >/dev/null
wait_for_service product-catalog

CATALOG_ROUTE_DISABLED=true docker compose -f "$compose_file" up --detach --no-deps --force-recreate shell-bff >/dev/null
wait_for_service shell-bff
disabled_cookies="$work_dir/disabled-cookies"
authenticate "$disabled_cookies"
curl --fail --silent --show-error --cookie "$disabled_cookies" "$base_url/auth/session" \
  | grep -q '"catalogRouteEnabled":false'
disabled_status=$(curl --silent --show-error --cookie "$disabled_cookies" --output "$work_dir/disabled.json" \
  --write-out '%{http_code}' "$base_url/bff/catalog/categories/CATALOG1")
test "$disabled_status" = 404
grep -q '"error":"route_disabled"' "$work_dir/disabled.json"
legacy_location=$(curl --silent --show-error --cookie "$disabled_cookies" --output /dev/null \
  --write-out '%{redirect_url}' "$base_url/auth/legacy")
printf '%s' "$legacy_location" | grep -q '/legacy-session?code='

CATALOG_ROUTE_DISABLED=false docker compose -f "$compose_file" up --detach --no-deps --force-recreate shell-bff >/dev/null
wait_for_service shell-bff
restored_cookies="$work_dir/restored-cookies"
authenticate "$restored_cookies"
curl --fail --silent --show-error --cookie "$restored_cookies" "$base_url/auth/session" \
  | grep -q '"catalogRouteEnabled":true'
curl --fail --silent --show-error --cookie "$restored_cookies" \
  "$base_url/bff/catalog/categories/CATALOG1" > /dev/null
restoration_complete=1

echo 'Product Catalog fallback drill passed: failure sanitized, kill switch routed to legacy, and modern route restored.'
