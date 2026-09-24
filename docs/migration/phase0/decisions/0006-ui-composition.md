# ADR 0006: Build-time route composition with BFFs

- Status: Accepted 2026-09-24
- Owners/approvers: Product, UI architecture, Security, Accessibility — unassigned

## Decision

Use one web shell and build-time route packages initially. Browser code calls same-origin BFF endpoints. Every modern route is declared in the route manifest and receives the accessible Modern experience marker from the shell. Runtime micro-frontends require later evidence and an ADR.

## Consequences

The design system and web platform packages are versioned build dependencies; routes do not import each other's internals. BFFs own session/CSRF and UI composition but no authoritative domain rules.
