#!/bin/sh
set -eu

service_url=${MODERN_SERVICE_URL:-http://localhost:18082}
route_url=${MODERN_ROUTE_URL:-http://localhost:18080}

curl --fail --silent --show-error "$service_url/actuator/health/readiness" | rg -q '"status":"UP"'
curl --fail --silent --show-error "$service_url/api/v1/example" | rg -q 'template-service is ready'
curl --fail --silent --show-error "$route_url/api/v1/example" | rg -q 'template-service is ready'
# Anonymous shell access starts the BFF login flow while retaining the trusted
# route classification. Authenticated marker behavior is covered by BFF and
# shell tests without requiring a developer's interactive Entra credentials.
curl --silent --show-error --head "$route_url/modern/" | rg -qi '^Location: .*/oauth2/authorization/entra'
curl --fail --silent --show-error --head "$route_url/modern/" | rg -qi '^X-Application-Generation: modern'
curl --fail --silent --show-error --head "$route_url/legacy/webtools/control/main" \
    | rg -qi '^X-Application-Generation: legacy'

# A caller cannot spoof the trusted route classification in either direction.
test "$(curl --fail --silent --show-error --head \
    -H 'X-Application-Generation: legacy' "$route_url/modern/" \
    | awk 'tolower($1) == "x-application-generation:" { gsub("\r", ""); print $2 }')" = modern
test "$(curl --fail --silent --show-error --head \
    -H 'X-Application-Generation: modern' "$route_url/legacy/webtools/control/main" \
    | awk 'tolower($1) == "x-application-generation:" { gsub("\r", ""); print $2 }')" = legacy

echo 'Modern paved-road and Phase 2 edge smoke checks passed.'
