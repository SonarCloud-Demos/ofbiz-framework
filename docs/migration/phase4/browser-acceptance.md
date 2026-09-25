# Product Catalog browser acceptance

## Automated gate

`cd web && npm run check` runs the Product Catalog candidate in pinned Playwright Chromium against the actual shell HTML, CSS, route manifest and JavaScript. A same-origin fixture supplies only the reviewed identity and catalog BFF responses, keeping this suite deterministic and independent of cloud infrastructure.

The suite proves:

- the authenticated category browse and keyword-search journey renders without a Modern experience marker;
- the journey can be completed with keyboard focus and Enter activation, including focus transfer to product detail;
- axe-core reports no WCAG 2.0/2.1 A or AA violations in the loaded catalog view;
- a disabled catalog cohort redirects the complete route to `/auth/legacy`;
- an unavailable catalog service exposes a sanitized message and an explicit legacy fallback, without leaking the upstream error code.

The pull-request workflow installs Node.js 24.11.1, immutable npm dependencies and the Playwright Chromium build before running this gate. Traces and the HTML report are retained by Playwright on failure.

## Scope and remaining evidence

This closes the repository-controlled browser accessibility and fallback gate. It does not substitute for testing the deployed legacy-session exchange, organization-supported browser versions, assistive-technology review, production content, load behavior or a production-like fallback drill. Those remain required before changing the route manifest from `candidate` to `modern`.
