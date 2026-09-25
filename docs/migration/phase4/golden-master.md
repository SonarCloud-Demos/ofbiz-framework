# Product Catalog demo-data golden master

## Evidence boundary

The committed golden master at `contracts/fixtures/product-catalog-demo-golden.json` is derived only from `applications/datamodel/data/demo/OrderDemoData.xml`. It contains no production observations or data and is explicitly marked `productionEvidence: false`.

The fixture fixes a reviewable repository baseline for:

- the `CATALOG1` normalized category fields and direct child IDs;
- every direct `100` category membership in catalog sequence order;
- normalized public fields for `GZ-1000`;
- exact name-sorted results for `tiny`, `open standards` and `gizmo`;
- exact empty behavior for `no-such-demo-product`.

## Automated comparison

`./gradlew modernCatalogGoldenCheck` parses the committed OFBiz XML, applies the approved export normalization and modern browse/search ordering, and compares the result with the golden fixture. The task is attached to root `check` and `build`.

The Product Catalog PostgreSQL integration suite loads the same source XML through the projection repository and compares real modern category, browse and search queries with the same fixture when Docker is available. This detects drift between legacy demo data, the migration normalization and modern SQL behavior.

## Limitations

These cases characterize deterministic repository behavior, not customer relevance. They do not prove the deployed storefront route, production catalog membership, locale/content fallback, query distribution, result quality, volume or latency. Production-derived golden cases and business acceptance remain deferred until that evidence is available; the demo fixture must not be relabeled as production acceptance.
