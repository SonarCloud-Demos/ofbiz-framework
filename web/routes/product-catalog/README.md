# Product Catalog route

Route: `/modern/catalog`

Status: candidate. The route deliberately does not display the Modern experience marker until its authenticated hybrid smoke, accessibility and fallback gates pass.

The browser calls only `/bff/catalog`. The BFF allowlists category, browse, product-summary and search requests and does not expose ingestion, projection or generic OFBiz endpoints.
