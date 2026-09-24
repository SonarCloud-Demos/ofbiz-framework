#!/bin/sh
set -eu

route_url=${MODERN_ROUTE_URL:-http://localhost:18080}
work_dir=$(mktemp -d "${TMPDIR:-/tmp}/ofbiz-login-smoke.XXXXXX")
cookies="$work_dir/cookies"
login_page="$work_dir/login.html"
result_page="$work_dir/result.html"

cleanup() { rm -rf "$work_dir"; }
trap cleanup EXIT HUP INT TERM

curl --fail --silent --show-error --location --max-redirs 5 \
    --cookie-jar "$cookies" --cookie "$cookies" \
    "$route_url/catalog/products" > "$login_page"

form_action=$(sed -n 's/.*<form id="kc-form-login"[^>]*action="\([^"]*\)".*/\1/p' "$login_page" \
    | sed 's/&amp;/\&/g' | head -1)
test -n "$form_action"

curl --fail --silent --show-error --location --max-redirs 10 \
    --cookie-jar "$cookies" --cookie "$cookies" \
    --data-urlencode 'username=catalog' --data-urlencode 'password=catalog' \
    --data-urlencode 'credentialId=' "$form_action" > "$result_page"

curl --fail --silent --show-error --cookie "$cookies" "$route_url/bff/session" \
    | rg -q '"authenticated":true'
curl --fail --silent --show-error --cookie "$cookies" \
    "$route_url/bff/catalog/products?q=demo" | rg -q 'PHASE3-DEMO-1'

echo 'Local OIDC login and CATALOG authorization checks passed.'
