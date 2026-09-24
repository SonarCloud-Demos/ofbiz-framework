# Legacy catalog browse/search inventory

Status: repository-static baseline captured on 2026-09-24. Runtime traces and production deployment inventory remain required.

## Finding: storefront component is absent

This repository checkout does not contain a registered `ecommerce` web application or controller. It does contain demo records and shared scripts that refer to `component://ecommerce`, showing that a storefront exists in the wider OFBiz ecosystem, but those references do not provide an executable legacy customer route here.

The executable browse/search behavior in this checkout is:

| Surface | Route | Access | Behavior |
| --- | --- | --- | --- |
| Order manager | `/ordermgr/control/category?category_id=...` | HTTPS, authenticated | category content and effective-dated members, default page size 10 |
| Order manager | `/ordermgr/control/product?product_id=...` | HTTPS, authenticated | product detail, catalog/store eligibility and order-entry concerns |
| Order manager | `/ordermgr/control/keywordsearch` | HTTPS, authenticated | session-backed keyword constraints, sorting and pagination |
| Order manager | `/ordermgr/control/advancedsearch` | HTTPS, authenticated | search constraint entry |
| Catalog manager | `/catalog/control/keywordsearch` | HTTPS, authenticated | administrative search and mutation actions |
| Catalog manager | `/catalog/control/advancedsearch` | HTTPS, authenticated | administrative constraint entry |

The catalog-manager surface is not a customer journey. The order-manager surface is the closest executable characterization source, but it is coupled to carts, price calculation, inventory, suppliers, reviews and product configuration. Phase 4 must not copy those couplings into Product Catalog.

Decision for repository implementation: build `/modern/catalog` as a new read-only customer experience and use the shared legacy domain behavior plus deterministic demo data as its characterization baseline. Do not claim that an existing customer storefront route has been retired. Before any real production cutover, inventory the deployed storefront/plugin and amend this record with its exact routes.

## Route dependency trace

### Category browse

The `category` controller renders `OrderEntryCatalogScreens#category`, which invokes `Category.groovy` and `CategoryDetail.groovy`.

Core behavior retained by the slice:

- resolve the active catalog and store;
- load `ProductCategory` and localized category content;
- call `getProductCategoryAndLimitedMembers`;
- filter effective-dated memberships;
- apply catalog view-allow and store out-of-stock rules where configured;
- sort by `sequenceNum`, then `productId`;
- return bounded, deterministic pages.

Legacy entities/views directly implicated include `ProductCategory`, `ProductCategoryMember`, `ProductCategoryRollup`, `ProdCatalog`, `ProdCatalogCategory`, `ProductStore`, `ProductStoreCatalog`, `ProductCategoryContentAndInfo`, `ElectronicText`, and the category-member view selected by `CategoryServices`.

### Keyword search

The `keywordsearch` controller invokes `ProductSearchSession`, then renders `OrderEntryCatalogScreens#keywordsearch` and a product summary per result.

Core behavior retained by the first slice:

- scope results to the selected catalog/search category;
- match product keywords;
- optionally constrain by category;
- produce a deterministic sort and bounded page;
- return an explicit empty state.

The legacy implementation stores constraints in the HTTP session and supports a much broader query language. The modern API deliberately begins with stateless `q`, `categoryId`, `cursor`, `limit`, and `sort` parameters. Advanced feature, supplier, price and inventory constraints are excluded.

Direct search dependencies include `Product`, `ProductKeyword`, `ProductCategoryMember`, `ProductCategoryRollup`, `ProductFeature`, `ProductFeatureAppl`, `ProdCatalogCategory`, and `ProductStoreKeywordOvrd`. Only the minimum approved projection is migrated.

### Product summary

The legacy `ProductSummary.groovy` also calculates price, checks inventory, reads supplier lead time, reviews, variants and configuration. Those are cross-context dependencies and are excluded from the first Product Catalog response.

The initial summary projection is limited to:

- `productId`;
- display `name` and optional plain-text `description`;
- primary image reference when classified for public display;
- product type and variant flags needed to form a stable browse result;
- effective category membership and display sequence.

No price, stock, supplier, review, cost, cart, promotion or order field is included.

## Deterministic repository dataset

Repository tests use the existing demo seed:

- product store: `9000`;
- website metadata: `WebStore` (the corresponding executable storefront is absent);
- catalog: `DemoCatalog`;
- browse root: `CATALOG1`;
- search category: `CATALOG1_SEARCH`;
- first-level browse categories: `100` (Gizmos) and `200` (Widgets).

The fixture is suitable for contract, ordering, pagination, empty-result, invalid-identifier and reconciliation tests. It is not a production volume or relevance baseline.

## Open evidence gates

- Capture runtime traces from the actual deployed customer storefront.
- Confirm whether anonymous browsing is permitted and which catalog/store is selected per host.
- Record production result fields, URL/SEO behavior, locale fallbacks and content sanitization.
- Measure catalog size, query mix, peak rate and latency percentiles.
- Establish relevance golden cases with business acceptance.
- Decide whether out-of-stock filtering belongs in the first UI; Inventory remains the fact owner.
