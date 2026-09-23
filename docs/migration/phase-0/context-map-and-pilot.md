# Phase 0 context map and first-pilot assessment

## Working context map

This is a discovery hypothesis derived from source structure and current ERP concepts. Domain owners must validate language, invariants, ownership, and event flows before it becomes target design.

```text
                         Identity / tenancy
                                |
 Party / customer ---> Order capture <--- Catalog ---> Pricing
       |                  |   |              |
       |                  |   +----------> Inventory / facilities
       |                  |                      |
       |                  +----------------> Fulfillment / shipment
       |                                         |
       +--------------> Billing / invoicing <----+
                              |
                           Payment
                              |
                        General ledger

 Work / HR <------ Manufacturing ------> Inventory / costing

 Marketing / content ---> Party + Catalog + Order read models
 Notification <---------- domain events from all contexts
 Reporting/analytics <---- events and governed exports
```

Key relationship classifications to validate:

| Upstream | Downstream | Working relationship | Risk to investigate |
| --- | --- | --- | --- |
| Party/customer | Order, billing, marketing, HR | Published profile/role identifiers and events | Current direct party reads and PII replication |
| Catalog | Pricing, order, inventory, marketing | Product identity/sellability and product-change events | Product component currently mixes all these behaviors |
| Pricing | Order | Synchronous price decision plus versioned decision evidence | Rules may depend on party, store, quantity, currency, date, promotions |
| Inventory | Order/fulfillment/manufacturing | Availability query, reservation commands, movement events | Reservation consistency and facility ownership |
| Order | Fulfillment, billing, payment | Order lifecycle events and explicit commands | Existing single transaction and Service ECAs |
| Billing/payment | Ledger | Balanced posting instructions/results | Financial reconciliation, period close, audit, idempotency |
| Manufacturing | Inventory/work/costing | Material reservations/movements and work events | Long-running production workflows and shared product model |
| All domains | Notification/reporting | Immutable integration events | Data minimization, ordering, replay, report freshness |

## Candidate scoring method

Score 1 (favorable/low difficulty) to 5 (unfavorable/high difficulty). “Learning value” is reversed: 5 means the slice exercises more of the desired platform. Scores are provisional because production usage and business priority are unknown.

| Candidate | Business risk | Data coupling | Hidden behavior | UI scope | Learning value | Reversibility | Assessment |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Read-only product search and basic detail | 2 | 3 | 2 | 2 | 5 | 1 | **Recommended provisional pilot** |
| Party/customer search and basic detail | 3 | 3 | 2 | 2 | 5 | 1 | Strong alternative, but PII/security burden is higher |
| Notification preferences | 2 | 2 | 2 | 2 | 4 | 2 | Potentially small, but current user journey/business value needs proof |
| Order inquiry/detail | 3 | 5 | 4 | 3 | 5 | 2 | Valuable second slice; too many composed domains for the first |
| Product maintenance/write workflow | 3 | 4 | 4 | 4 | 5 | 3 | Follow the read pilot after ownership and ECA analysis |
| Checkout/order capture | 5 | 5 | 5 | 5 | 5 | Explicitly unsuitable as first pilot |
| Invoice or ledger posting | 5 | 5 | 5 | 4 | 5 | Requires financial parallel run and mature platform |

## Provisional pilot: product catalog search and basic detail

### User outcome

An authorized back-office catalog user can search products and open a basic product detail page in the modern shell. The result is functionally equivalent for the agreed fields and filters, visibly marked **Hybrid** during shadow/legacy ownership and **Modern** only after the route no longer needs OFBiz at runtime.

### Proposed in-scope behavior

- a modern `/catalog/products` search/list route;
- a modern `/catalog/products/{productId}` basic-detail route;
- agreed search keys such as product ID, internal name/name, type, status, and good identification, subject to product-owner validation;
- basic identity, lifecycle/status, dates, primary description, identifiers, and category summary fields;
- `CATALOG`-equivalent view authorization and tenant/data-scope enforcement;
- accessible loading, empty, error, and unauthorized states;
- trusted route-generation badge/header/telemetry specified in the migration plan;
- paginated API with deterministic ordering, limits, and input validation;
- shadow replication/backfill, comparison, reconciliation, and rollback to the OFBiz `FindProduct`/`EditProduct` read experience.

### Explicitly out of scope for the first cut

- create/update/delete product;
- pricing and promotions;
- inventory, ATP, facility locations, and reservations;
- costing, accounting mappings, fixed assets, suppliers, subscriptions, rentals, and manufacturing;
- product configuration/variants beyond a basic non-editable relationship summary;
- binary/image/content management beyond an approved existing public/read adapter;
- ecommerce storefront behavior;
- removal of OFBiz as product system of record.

Keeping these exclusions visible prevents the 875-service/764-route product component from becoming the pilot scope.

### Candidate data set

Validate the exact fields and relationships in workshops. Likely source entities include `Product`, `GoodIdentification`, `ProductCategory`, `ProductCategoryMember`, selected type/status/enumeration reference data, and narrowly selected content/text records. Category and content may remain legacy-owned projections initially. Do not replicate pricing, inventory, party, or accounting tables merely because current OFBiz views join them.

### Pilot APIs and events to design, not yet commit

- `GET /api/catalog/products` with explicit filters, cursor/page contract, field-level authorization, and stable error model;
- `GET /api/catalog/products/{id}`;
- internal ingestion contract for initial backfill and changes while OFBiz owns writes;
- `ProductCatalogProjectionUpdated` as an internal projection event only if needed; do not claim a domain event until the catalog service owns the business change.

### Required invariants and comparison rules

The product owner and data owner must approve:

- product identity and uniqueness rules, including case sensitivity and `GoodIdentification` uniqueness;
- status/date visibility rules and treatment of variants/virtual products;
- tenant/store/catalog scoping and authorization;
- search normalization, collation, sorting, pagination, and locale behavior;
- which description/content wins by locale/date/purpose;
- acceptable replication lag and behavior during stale/unavailable projection;
- record/count/checksum comparison and sampled field equality;
- expected behavior for malformed, duplicate, orphaned, expired, and deleted records.

### Pilot success criteria

- 100% of agreed golden test cases return equivalent authorized results;
- no unauthorized cross-tenant or field access in automated and penetration tests;
- reconciliation reaches the approved mismatch tolerance and stays there for the soak period;
- P95 search/detail latency meets an approved SLO at representative data volume;
- the marker, response header, trace owner, feature flag, and actual backend path agree in every end-to-end test;
- switching the route back to OFBiz requires no data repair because OFBiz remains writer during this read pilot;
- support can identify and diagnose the route using a correlation ID without accessing sensitive data;
- the team demonstrates local build/start, Azure deployment through Terraform, canary, observability, backup/restore where state exists, and rollback.

### What this pilot proves and does not prove

It proves the monorepo paved road, edge/identity/BFF, UI shell and marker, legacy anti-corruption/ingestion, service-owned projection, reconciliation, telemetry, IaC, CI/CD, and safe route strangling. It deliberately does **not** prove command ownership, cross-service sagas, financial reconciliation, or OFBiz write retirement. A subsequent product-maintenance slice or another bounded command journey must prove those before extraction accelerates.

## Pilot decision gate

Approve this pilot only after answering:

1. Is `/catalog` deployed and used, and which exact search/detail routes dominate traffic?
2. Are optional plugins/custom screens the real product UI?
3. Which fields and filters are essential to the users' task?
4. Does product visibility vary by tenant, organization, store, catalog, role, date, or locale?
5. What data volumes, update rate, latency, freshness, and availability are required?
6. Who owns catalog semantics and can approve equivalence?
7. Does the chosen journey have enough business value to justify being the platform pilot?

If it fails this gate, score party/customer basic inquiry next, applying stricter PII controls, or choose notification preferences if a real bounded journey is confirmed.
