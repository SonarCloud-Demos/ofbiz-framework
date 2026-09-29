# Initial slice backlog and recommendation

Status: revised and approved on 2026-09-24. Scores remain hypotheses to validate with production evidence.

Scale: 1 is low/unfavorable and 5 is high/favorable. For “blast radius” and “data sensitivity,” a higher score means safer/lower. Total is unweighted and is used only to make assumptions visible.

| Candidate vertical slice | Business learning/value | Boundary clarity | Low blast radius | Low data sensitivity | Platform coverage | Static confidence | Total / 30 | Principal unknowns |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Outbound notification delivery plus delivery-status admin route | 3 | 4 | 4 | 3 | 5 | 3 | 22 | actual providers/callers, consent ownership, delivery volumes |
| Product category browse/search route | 4 | 3 | 4 | 5 | 3 | 2 | large product surface, pricing/store eligibility, content coupling |
| Party contact details route | 4 | 3 | 3 | 1 | 3 | 3 | privacy, identity mapping, broad downstream use |
| Inventory availability/reservation route | 5 | 3 | 1 | 4 | 5 | 3 | transactional invariants, peak concurrency, order/manufacturing coupling |

## Approved first slice

The approved first slice is **Product category browse/search plus its modern customer-facing route**. The project changed the selection from outbound notification delivery on 2026-09-24. Although notifications scored one point higher in the initial unweighted assessment, Product category browse/search was selected to validate a recognizable customer journey and the modern UI migration while retaining a read-heavy, low-blast-radius first cutover.

The slice boundary is category navigation, product discovery and the product summary fields required for browse/search. Pricing, promotions, inventory availability, ordering and product maintenance remain outside the slice unless their read contracts are explicitly approved. The Product Catalog context owns its extracted browse/search model and must not query the legacy database at runtime after cutover.

Outbound notification delivery returns to the prioritized backlog for a later migration wave.

## Acceptance gates before selection is final

- enumerate the current category browse/search routes, entities, services and content dependencies;
- define the category, product-summary, pricing, store-eligibility and content fields that are in or out of scope;
- measure catalog size, query volume, peak rate, latency and search-result relevance against a reproducible dataset;
- define the backfill/change-feed mechanism and prove shadow-result reconciliation and route fallback;
- define the modern customer route, permissions, SLO, RPO/RTO, cache/search-index recovery and support owner;
- confirm business value and that the slice is representative enough to validate the platform;
- obtain product, engineering, security/privacy and SRE approval below.

| Approval | Owner | Decision/date |
| --- | --- | --- |
| Product value and route scope | Project approval | Product category browse/search approved 2026-09-24 |
| Domain boundary and data ownership | Project approval | Product Catalog boundary approved 2026-09-24; exact fields remain a slice input |
| Security/privacy and classification | Project approval | Approved 2026-09-24; detailed controls remain slice inputs |
| SLO/operations/on-call | Project approval | Approved 2026-09-24; measured targets remain slice inputs |
| Migration/cutover safety | Project approval | Approved 2026-09-24; reconciliation and cutover plan still require evidence |

If implementation evidence invalidates this selection, retain the scoring method, update the assumptions, and approve a replacement explicitly.
