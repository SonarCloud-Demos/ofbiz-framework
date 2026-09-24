#!/bin/sh
set -eu

base_url="${MODERN_BASE_URL:-http://localhost:8080}"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT HUP INT TERM

curl --fail --silent --show-error "$base_url/modern/platform" > "$work_dir/modern.html"
curl --fail --silent --show-error "$base_url/route-manifest.json" > "$work_dir/routes.json"
curl --fail --silent --show-error "$base_url/api/platform" > "$work_dir/platform.json"
curl --fail --silent --show-error "$base_url/legacy/webtools/control/main" > "$work_dir/legacy.html"
curl --fail --silent --show-error http://127.0.0.1:10000/health > "$work_dir/storage.json"
curl --fail --silent --show-error --user modern:local-messaging-only http://127.0.0.1:15672/api/health/checks/alarms > "$work_dir/messaging.json"
curl --fail --silent --show-error \
  --data-urlencode client_id=modern-shell \
  --data-urlencode username=developer \
  --data-urlencode password=local-development-only \
  --data-urlencode grant_type=password \
  http://127.0.0.1:8180/realms/ofbiz-development/protocol/openid-connect/token > "$work_dir/token.json"

grep -q 'app.js' "$work_dir/modern.html"
grep -q 'Modern experience' "$work_dir/routes.json"
grep -q 'platform-sample' "$work_dir/platform.json"
grep -q 'local-blob-port' "$work_dir/storage.json"
grep -q 'ok' "$work_dir/messaging.json"
grep -q 'access_token' "$work_dir/token.json"
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
