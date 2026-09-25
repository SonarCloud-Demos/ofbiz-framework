# Phase 4 — Product category browse/search

## Status

Started on 2026-09-24. The approved first production vertical slice is Product category browse/search. The service, projection, BFF and UI candidate are implemented and pass the local authenticated hybrid smoke path; traffic cutover and production readiness are not yet claimed.

The project explicitly accepted beginning the slice with named owners unassigned. This is a temporary governance exception, not evidence of operational readiness. Named accountable owners, an on-call rotation and an escalation route remain required before production traffic is enabled.

## Working documents

- [Slice charter](slice-charter.md) defines the initial boundary, user journey, contracts, data ownership, safety constraints and evidence gates.
- [Legacy catalog inventory](legacy-catalog-inventory.md) records the executable routes, dependencies, deterministic dataset and missing-storefront finding.
- [Product Catalog OpenAPI](../../../contracts/openapi/product-catalog.yaml) fixes the initial read-only service boundary.
- [Projection ingestion OpenAPI](../../../contracts/openapi/product-catalog-ingestion.yaml) fixes the private migration boundary.
- [Projection runbook](projection-runbook.md) defines idempotent backfill, reconciliation and recovery.
- [Change-capture decision](change-capture.md) defines the transitional polling boundary and the event-driven replacement gate.
- [Browser acceptance](browser-acceptance.md) records automated accessibility, keyboard and fallback coverage.
- [Demo-data golden master](golden-master.md) records deterministic legacy-to-modern comparisons and their non-production boundary.
- [SLO/load deferral](slo-load-deferral.md) records the development-only exception and mandatory production reopen conditions.
- [Support runbook](support-runbook.md) defines detection, complete route fallback, recovery and escalation gates.
- The [migration ledger](../phase0/migration-ledger.csv) tracks cutover state and ownership.
- The [initial slice backlog](../phase0/slice-backlog.md) records the selection decision and alternatives.

## Current evidence

The local hybrid stack now demonstrates:

1. an authenticated `/modern/catalog` candidate route through the shell and BFF;
2. an automated, non-destructive OFBiz export and projection run containing 40 categories, 72 products and 112 memberships in the deterministic demo dataset;
3. category navigation, product browse and keyword search through the browser-facing BFF;
4. denial of unauthenticated catalog BFF requests;
5. service, BFF, Groovy, contract/schema, Compose and expanded hybrid smoke checks;
6. search throttling and browser security headers at the same-origin edge;
7. PostgreSQL integration tests for Flyway migrations, idempotent updates and guarded reconciliation (executed when Docker is available);
8. Chromium keyboard, WCAG A/AA, disabled-route fallback and sanitized service-failure tests;
9. committed-demo category, membership, field, ordering, relevance and zero-result golden comparisons against modern PostgreSQL behavior.

The route manifest intentionally remains `candidate` and the catalog page has no Modern experience marker. Numeric SLO/load work is explicitly deferred for development, and production-derived relevance acceptance, deployed-storefront characterization, production-like reconciliation, a deployed fallback drill and operational ownership remain open.

## Implemented service increment

`services/product-catalog-service` is registered in the root Gradle build and currently provides:

- the read-only category, category-products, product-summary and keyword-search endpoints defined by the OpenAPI contract;
- bounded identifiers, queries and page sizes with opaque cursors;
- deterministic catalog/name ordering, empty-result behavior and sanitized problem responses;
- correlation identifiers in browse/category responses;
- an independently owned PostgreSQL projection schema managed by Flyway;
- liveness/readiness endpoints, an executable Spring Boot artifact and a non-root container image;
- focused tests for category navigation, pagination, empty search and invalid cursors.

Projection ingestion, guarded snapshot reconciliation, the narrow OFBiz/adapter export boundary, transitional polling change capture, the automated non-destructive backfill coordinator, the BFF and the modern UI candidate route are implemented. The local coordinator keeps reconciliation disabled because an HTTP cutoff does not provide a cross-request database snapshot. Browser accessibility, route-level fallback and a reversible local hybrid fallback drill are automated; production-like reconciliation/load evidence and a deployed fallback drill remain open.
