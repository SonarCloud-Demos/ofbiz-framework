#!/bin/sh
set -eu

service_url=${MODERN_SERVICE_URL:-http://localhost:18082}
route_url=${MODERN_ROUTE_URL:-http://localhost:18080}

curl --fail --silent --show-error "$service_url/actuator/health/readiness" | rg -q '"status":"UP"'
curl --fail --silent --show-error "$service_url/api/v1/example" | rg -q 'template-service is ready'
curl --fail --silent --show-error "$route_url/api/v1/example" | rg -q 'template-service is ready'
curl --fail --silent --show-error "$route_url/" | rg -q 'Application generation: modern'
curl --fail --silent --show-error --head "$route_url/" | rg -qi '^X-Application-Generation: modern'
curl --fail --silent --show-error --head "$route_url/legacy/webtools/control/main" \
    | rg -qi '^X-Application-Generation: legacy'

echo 'Modern paved-road smoke checks passed.'
