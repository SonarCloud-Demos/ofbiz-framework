# Phase 3 — product catalog pilot

## Status

Phase 3 started on 2026-09-24. Repository implementation is complete, but Phase
3 is **in progress**. Production evidence, stakeholder approval, canary, soak,
game day and legacy removal have not been completed, so the exit gate has not
passed.

## Implemented increment

The provisional Phase 0 choice is implemented as a read-only product search and
basic-detail projection. OFBiz remains the authoritative writer. The modern
service owns an isolated PostgreSQL schema and Flyway migration; it has no OFBiz
code or database dependency. An authenticated BFF requiring the `CATALOG` role
exposes the browser contract. `/catalog/products` renders in the modern shell
with a trusted **Hybrid** marker.

The keyed OFBiz anti-corruption endpoint exports deterministic pages using a
timestamp/product-ID cursor. The service polls it, commits idempotent upserts
and checkpoints atomically, and performs cutoff-bounded snapshot reconciliation
to detect physical deletions. Source records carry checksums. This is a complete
repository implementation, not evidence that production data has been compared.

Local developers can run `./modern up`, then `./modern seed`, and open
`http://localhost:18080/catalog/products`. Operational rollout, reconciliation,
restore and rollback procedures are in [operations-runbook.md](operations-runbook.md).
The full profile builds the workspace OFBiz `demo` image so its private export
adapter matches the checked-out code; the first build is intentionally slower.

The local OIDC provider seeds two development-only users. `catalog` / `catalog`
has `CATALOG` and `LEGACY_USER`; `viewer` / `viewer` intentionally lacks
`CATALOG` for authorization testing. These credentials and the local HTTP-cookie
profile must never be used outside the Compose development topology.

## Ownership and rollback

- OFBiz owns product commands and source data.
- `product-catalog-service` owns only its disposable read projection.
- Rollback routes `/catalog/products` to the OFBiz `FindProduct` experience and
  requires no reverse data repair.
- The migration data owner approves checksums, golden cases, mismatch tolerance,
  and freshness before any canary.

See [exit-gate.md](exit-gate.md) for completed and outstanding evidence.
