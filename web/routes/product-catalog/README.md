# Product Catalog route

Route: `/modern/catalog`

Status: candidate. Authenticated hybrid smoke plus browser accessibility and route-level fallback automation pass. The route deliberately does not display the Modern experience marker until production-like reconciliation/SLO evidence, a deployed fallback drill and operational ownership are accepted.

The browser calls only `/bff/catalog`. The BFF allowlists category, browse, product-summary and search requests and does not expose ingestion, projection or generic OFBiz endpoints.
