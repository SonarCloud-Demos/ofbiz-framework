# Product Catalog projection runbook

Status: repository implementation baseline on 2026-09-24. The narrow OFBiz export and adapter boundary are implemented; production deployment evidence and scheduling remain open.

## Safety model

- The Product Catalog runtime reads only its PostgreSQL projection.
- Migration tooling sends bounded records through the internal ingestion contract; it never grants the modern service access to OFBiz tables.
- `CATALOG_INGESTION_KEY` is mandatory, must contain at least 32 bytes and belongs in the secret store outside local development.
- Public edge routing must deny `/internal/*`.
- A batch and its source checkpoint commit in one database transaction.
- Upserts use source checksums so replaying a batch is idempotent.
- Snapshot completion compares the declared source count with the distinct records received for that run. A mismatch returns `409` and deletes nothing.
- Foreign-key order ensures memberships are removed before stale products or categories.

## Backfill sequence

1. Create an opaque run ID and obtain a source-consistent view by quiescing catalog writes or exporting from a database snapshot/read replica. The paged HTTP service alone cannot create a cross-request database snapshot.
2. Export categories and products before memberships, in bounded pages.
3. Compute SHA-256 over each source record's canonical approved fields.
4. POST each page to `/internal/catalog/projection/batches` with the same `snapshotRunId`, an increasing opaque cursor and the ingestion key.
5. Count distinct category, product and membership records in the source snapshot.
6. POST that total to `/internal/catalog/projection/snapshots/{runId}/complete?source=ofbiz`.
7. Treat `409` as an incomplete transfer: do not retry completion until missing pages are replayed or abandon the run.
8. Compare modern and normalized legacy browse/search results before enabling any route cohort.

## Recovery

Batch replay is safe. Snapshot completion is single-use because the reconciliation record uses the run ID as its primary key. If completion fails before commit, retry it. If the source snapshot was inconsistent or its count cannot be proven, abandon the run ID and begin a new snapshot; never lower the declared count merely to make reconciliation pass.

## Export boundary

OFBiz exposes the authenticated `exportProductCatalogProjectionPage` service through `/rest/catalog-export/{recordType}`. It allows only categories, products and effective-dated memberships, carries a shared run cutoff, uses stable key cursors, caps pages at 500 and returns only approved projection fields with SHA-256 checksums. Stable cursors do not create transaction isolation across HTTP requests; source consistency must be established as described above.

The transitional adapter is the only intended caller. Its `/internal/catalog-export` endpoint requires the workload token, validates type and page size, mints a 30-second OFBiz JWT, and sanitizes dependency failures. Public edge routing must not expose either internal endpoint.

The Product Catalog service includes an automated coordinator that pages categories, products and memberships in foreign-key order, preserves one cutoff per run and applies each page transactionally. It rejects a changed cutoff, a non-advancing cursor and more than 100,000 pages.

`CATALOG_BACKFILL_ENABLED` activates synchronization. It is enabled locally with `CATALOG_BACKFILL_RECONCILE_ENABLED=false`, so repeated runs upsert current records but never remove stale rows. Enabling reconciliation records all snapshot IDs and completes guarded deletion only after the received count matches the coordinator's source count. Set that flag only while catalog writes are quiesced or the exporter reads a database snapshot/read replica.

The coordinator and hybrid configuration are implemented but a complete hybrid backfill has not yet been exercised as acceptance evidence.
