# Phase 3 route fallback runbook

1. Set `PLATFORM_ROUTE_DISABLED=true` on the shell BFF and deploy a new revision.
2. Verify `/auth/session` reports `platformRouteEnabled:false` for an authenticated test user.
3. Verify `/modern/platform` sends that user through `/auth/legacy`, the one-time exchange, and `/legacy/webtools/control/main` without exposing a token.
4. Confirm ordinary legacy paths still resolve directly to the Front Door legacy-default route and do not contain `data-experience="modern"`.
5. Inspect correlation IDs, BFF dependency failures, adapter circuit state, identity exchange audit events, and OFBiz login failures.
6. Restore the route only after its BFF and dependency health checks pass. Increase `PLATFORM_ROUTE_PERCENT` gradually; assignments are stable by immutable subject.

The test route owns no data, so fallback is safe. Future writer routes require migration-ledger approval before this procedure may be reused.
