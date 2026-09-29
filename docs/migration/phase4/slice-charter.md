# Product category browse/search slice charter

Status: initial charter approved for discovery on 2026-09-24. Items marked **TBD** are exit gates for discovery, not assumptions that implementation has satisfied.

## Outcome and users

Deliver a modern customer-facing journey in which a user can:

1. open a catalog category;
2. navigate its child categories;
3. browse eligible product summaries with stable pagination;
4. search the same eligible product set by keyword;
5. open a product summary from either result set;
6. distinguish loading, empty, invalid-query and unavailable states.

The initial journey is read-only at `/modern/catalog`. Anonymous versus authenticated access remains **TBD** pending inventory of the deployed storefront; the repository contains only authenticated order-entry and administrative catalog routes.

## Boundary

The Product Catalog context owns products, variants, categories, features, catalog membership, descriptive attributes and sellable/purchasable lifecycle, consistent with the intended architecture.

Included for this slice:

- category identity, hierarchy, name, description and approved presentation metadata;
- product identity, category membership, name, description, primary image reference and approved summary attributes;
- catalog/store eligibility facts necessary to decide whether an item appears;
- browse ordering, keyword matching and deterministic pagination;
- a read-optimized Product Catalog database or search projection owned by the modern service;
- modern route, shell integration, BFF contract, telemetry and route-level fallback.

Excluded unless separately approved:

- calculated prices, price lists and promotions;
- inventory quantities or availability promises;
- carts, checkout, ordering and recommendations;
- product/category creation or maintenance;
- content asset ownership (the slice stores stable Content IDs or URLs only);
- direct runtime reads from the OFBiz database by modern code.

If the usable legacy journey cannot omit price, the UI may consume a versioned Pricing read contract; price ownership must not move into Product Catalog.

## Legacy discovery scope

The initial static evidence identifies browse/search entry points in the catalog and order-manager controllers, including `advancedsearch`, `keywordsearch`, `category` and `product`. Discovery must distinguish customer-facing storefront behavior from administrative catalog and order-entry behavior before the modern route is fixed.

The inventory must record:

- exact routes, screens, services, entities, ECAs and permissions;
- catalog/store/category selection rules and effective-date behavior;
- locale, currency and content fallbacks;
- sort, pagination, keyword parsing and eligibility rules;
- dataset size, request volume, peak rate and latency distribution;
- callers or downstream journeys that depend on result shape or URLs.

## Proposed contracts

The initial service contract is committed at `contracts/openapi/product-catalog.yaml`:

- `GET /api/catalog/v1/categories/{categoryId}`
- `GET /api/catalog/v1/categories/{categoryId}/products?cursor=&limit=&sort=`
- `GET /api/catalog/v1/products/{productId}/summary`
- `GET /api/catalog/v1/search?q=&categoryId=&cursor=&limit=&sort=`
- `GET /bff/catalog/...` as the only browser-facing API boundary

Every collection response must have a deterministic order, bounded page size and opaque continuation cursor. Error responses must be sanitized and carry the shared correlation identifier. The OpenAPI contract and compatibility tests are required before service implementation is considered complete.

## Data ownership and migration

- Current system of record and writer: OFBiz entity engine.
- Target owner: Product Catalog service and its independently managed database/search projection.
- Initial transfer: repeatable snapshot/backfill from an approved OFBiz export or narrow adapter contract.
- Change capture during the read-only migration window uses bounded, checksum-based polling as recorded in `change-capture.md`. Writer transfer remains blocked until versioned catalog lifecycle events and an outbox/inbox path replace polling.
- Read confidence: shadow modern reads and compare them with normalized golden-master legacy results.
- Write transfer: outside this read-only slice. OFBiz remains the product-maintenance writer until a later explicitly chartered slice.
- Cutover: route reads only after reconciliation and SLO gates pass; preserve route-level fallback during a time-boxed observation window.

The modern runtime must never query OFBiz tables directly. Backfill and reconciliation tooling may access approved exports or adapter contracts and must be separately identifiable and auditable.

## Security and data classification

Expected result data is Public or Internal/public-subset; the field-level classification is **TBD**. Credentials, unpublished products, supplier terms, cost data, restricted content and internal eligibility rules must not leak into responses, logs or traces.

The route must enforce the reviewed anonymous/authenticated policy, validate identifiers and query bounds, rate-limit search, escape rendered content, apply browser security headers and test denied access to unpublished catalog data.

## SLO and acceptance measures

Numeric targets remain **TBD** pending baseline measurements. The approved targets must cover:

- browse/search availability and p50/p95/p99 latency;
- maximum result staleness after a legacy catalog change;
- result-set and field-level reconciliation accuracy;
- search relevance golden cases and zero-result behavior;
- peak throughput and cache/search-index recovery time;
- RPO/RTO, backup restoration and projection rebuild time;
- accessibility conformance and supported browsers.

Repository work proceeds under the development-only deferral in `slo-load-deferral.md`. The deferral does not satisfy or remove any production cutover criterion.

## Cutover and rollback

Ramp reads through staff, a small stable cohort and then increasing traffic. At every stage compare latency, errors, result counts, field values and agreed business signals. A kill switch must route the entire journey back to the legacy route without changing product data.

Rollback is read-only and must not require reverse data synchronization. Failed or stale modern projections are discarded and rebuilt from the approved source. The fallback observation window and retirement criteria are **TBD**.

## Ownership exception

Engineering, product, data, security/privacy and SRE owners, the primary on-call rotation and escalation route are intentionally unassigned at project direction on 2026-09-24. Work may proceed through discovery and implementation under this exception. Production traffic remains blocked until operational accountability is assigned and recorded in the migration ledger and deployable ownership metadata.

## Discovery exit checklist

- [ ] Deployed customer-facing legacy routes and dependencies are enumerated; repository-static inventory is complete.
- [x] One deterministic repository dataset is selected and classified for contract tests; production-like data remains required for cutover evidence.
- [x] Initial Product Catalog fields are bounded; any later cross-context read contract still requires explicit approval.
- [x] Committed-demo golden-master browse/search cases are reproducible; production-derived relevance acceptance remains deferred.
- [ ] Baseline volume, latency and relevance measurements are recorded.
- [x] Backfill, transitional change capture, reconciliation and rebuild designs are recorded; production-like execution evidence remains required.
- [ ] Numeric SLO, RPO/RTO and reconciliation thresholds are approved (development-only deferral recorded).
- [ ] OpenAPI service contract is committed; lint/compatibility automation and the BFF contract remain required.
- [x] Browser accessibility plan, repository fallback tests and the support runbook are recorded; a deployed drill remains required.
- [ ] Product Catalog threat model is approved.
