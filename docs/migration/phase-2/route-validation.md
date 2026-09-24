# Legacy route validation and rollback

## Route contract

`platform/routes.yaml` is the reviewed ownership catalog. Unmatched paths route
to OFBiz. `/modern/**` and `/bff/**` route to the authenticated BFF;
OAuth protocol endpoints route to the BFF without an existing session. The
template routes are explicitly development-only and are not business routes.

For each representative OFBiz application, capture a sanitized baseline both
directly and through the common edge:

- status, redirect location and response content checksum;
- request/response headers, excluding credentials and personal data;
- session and logout cookie behavior;
- multipart upload at typical and approved maximum sizes;
- client-IP/audit attribution;
- P50/P95/P99 latency and timeout behavior;
- generation header and trace route owner.

The local edge currently uses a 50 MiB body limit and 10/60/60-second
connect/send/read timeouts. These are explicit starting values, not production
requirements; compare them with the Phase 0 baseline before rollout.

## Synthetic coverage

`./modern check` verifies catalog/security structure. The BFF tests verify login
redirection, PKCE, authenticated session projection, CSRF rejection, role
enforcement and the signed legacy assertion. `./modern smoke` verifies target
health, generation headers and header-spoofing resistance against a running
hybrid topology.

## Fast rollback

Keep the pre-edge OFBiz endpoint restricted but operational during rollout.
Rollback changes Front Door/APIM origin/route configuration to that endpoint;
it does not alter OFBiz data. Preserve DNS TTL and access-control procedures in
the environment runbook. A rollback is complete only after direct legacy login,
critical journeys and audit attribution pass and edge traffic reaches zero.
