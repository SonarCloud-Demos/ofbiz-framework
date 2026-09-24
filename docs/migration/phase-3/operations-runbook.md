# Product catalog pilot operations runbook

## Ownership and service levels

The catalog domain owner owns product semantics; the migration data owner owns
projection correctness; the platform on-call owns runtime recovery. Initial
non-production objectives are 99.5% monthly availability, P95 search below
500 ms, P95 detail below 250 ms, projection lag below five minutes, and zero
unresolved authorization or reconciliation failures. Production objectives
require owner approval and measured baselines.

Alert on readiness failure, Container App restarts, HTTP 5xx rate, failed
`catalog.projection.sync` counters, reconciliation deletions above the approved
threshold, or an unchanged source cursor beyond five minutes. Correlate BFF,
service and OFBiz logs with the edge request ID; never log query text,
descriptions, tokens, keys or database credentials.

## Backfill and reconciliation

1. Confirm `/catalog/products` remains `hybrid` and OFBiz is authoritative.
2. Configure distinct database, ingestion and OFBiz export credentials.
   Confirm both export paths return 404 through the public edge and are
   reachable only on the private workload network.
3. Enable `CATALOG_LEGACY_SYNC_ENABLED`; monitor checkpoint advancement at
   `/internal/v1/catalog/reconciliation` from the private network.
4. Let the timestamp/product-ID cursor drain all pages. A page is committed
   atomically with its checkpoint and can be replayed safely.
5. Run the snapshot reconciliation. The cutoff prevents products created during
   the scan from being treated as deletions. Review source/projected counts and
   every deletion before canary approval.
6. Execute approved golden searches and sampled field/checksum comparisons.

## Rollback

Route `/catalog/products` to the legacy `FindProduct` experience. Disable
`CATALOG_LEGACY_SYNC_ENABLED` if the adapter contributes to the incident. OFBiz
requires no repair because it remains the sole writer. Preserve the projection
and logs for diagnosis; rebuilding the disposable projection is preferred to
manual row repair.

## Restore and replay game day

Restore PostgreSQL to an isolated server, deploy the same image digest with
sync disabled, and verify Flyway plus sampled records. Re-enable sync from the
last committed cursor and confirm idempotent replay. Exercise an invalid export
key, unavailable OFBiz, a poison record, a failed page, a stale cursor, snapshot
deletion, revision rollback, and edge fallback. Record timings, evidence and
owners in the exit gate before production canary.

## Cutover and removal

After the approved soak, prove normal UI/API paths make no OFBiz calls, change
the route and manifest together from `hybrid` to `modern`, and remove the legacy
navigation path. Removal of the export endpoint occurs only after the rollback
window and retention obligations expire.
