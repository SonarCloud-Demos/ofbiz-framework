# Product Catalog support and fallback runbook

## Scope

This runbook covers the read-only Product category browse/search candidate. OFBiz remains the writer and fallback. No reverse synchronization is required or permitted.

Named on-call and escalation assignments remain intentionally open. This runbook is executable development guidance, not evidence of production operational ownership.

## Detect and classify

Investigate when catalog readiness fails, BFF `upstream_unavailable` responses rise, polling runs fail consecutively, projection age grows, or browse/search comparison diverges.

1. Capture the correlation ID, route, UTC time and affected cohort without recording credentials or unrestricted personal data.
2. Check shell BFF, Product Catalog and PostgreSQL readiness and recent logs.
3. Classify the incident as routing/identity, service, projection freshness, source export or database failure.
4. Do not repair projection data manually or enable reconciliation against an inconsistent HTTP scan.

## Fall back

Set `CATALOG_ROUTE_DISABLED=true` in the environment configuration and deploy/recreate the shell BFF. The session response must report `catalogRouteEnabled:false`; the browser then sends the entire journey through `/auth/legacy`. Confirm the BFF rejects direct catalog requests with `404 route_disabled` and that the legacy-session exchange redirects successfully.

Do not use percentage rollout during an incident requiring complete fallback. Do not stop OFBiz writes.

For the local hybrid stack, run:

```sh
./modern fallback-drill
```

The drill verifies baseline modern access, sanitized behavior while Product Catalog is stopped, the complete kill switch and legacy exchange, restoration of Product Catalog, and re-enablement of the modern candidate. Its trap attempts to restore both services after failure or interruption.

## Recover

1. Keep the route disabled while diagnosing and repairing the modern path.
2. If projection integrity is uncertain, discard and rebuild it using a new source-consistent snapshot as defined in `projection-runbook.md`.
3. Reconcile counts and golden comparisons; verify readiness, browser acceptance and authenticated hybrid smoke.
4. Re-enable for staff only, observe, then resume the previously approved cohort. Never skip directly to a larger cohort after recovery.
5. Record trigger, timings, evidence, data decisions and follow-up actions.

## Escalation and production gate

Before production traffic, replace the unassigned placeholders with named product, engineering, data, security/privacy and SRE owners, a primary on-call rotation and a tested escalation path. The numeric fallback observation window and retirement criteria remain part of the deferred SLO/load gate.
