# Product Catalog transitional change capture

## Decision

During the read-only migration window, OFBiz remains the catalog system of record and Product Catalog uses bounded polling rather than introducing a legacy outbox. The backfill coordinator runs on a configurable fixed delay (five minutes by default), reads the allowlisted OFBiz export in stable-key pages and checksum-upserts changed categories, products and memberships into the owned projection.

This is deliberately transitional. It avoids adding a second writer or generic database access to OFBiz while the first slice is still proving its boundary. A later write-owning catalog slice must replace polling with versioned domain events and an outbox/inbox path before writer ownership moves.

## Safety and recovery

- Each page and its checkpoint commit in one Product Catalog transaction. A failed page can be replayed safely.
- Checksums make unchanged category and product replay a no-op; membership keys make membership replay idempotent.
- Only a source-consistent, counted snapshot may enable reconciliation. Count mismatch returns `409` and deletes nothing.
- Normal polling runs with reconciliation disabled, so an inconsistent HTTP scan cannot delete projection rows.
- Removals are applied only by an operator-controlled consistent snapshot as described in [projection-runbook.md](projection-runbook.md).
- A damaged or stale projection is disposable: stop routing, recreate the schema, replay a consistent snapshot, reconcile counts, and resume shadow comparison.

## Operational signals

Every run logs its opaque run ID, received record count, reconciliation mode and outcome. Alerting must cover consecutive failed runs and projection age. The production maximum staleness threshold remains blocked on the approved numeric SLO; the polling interval must be lower than that threshold with enough margin for retry and paging.

The local deterministic path proves repeated checksum updates, guarded completion and rebuild mechanics. Production authorization still requires a source-consistent snapshot, measured transfer duration, deletion reconciliation, failure injection and observed staleness under production-like load.
