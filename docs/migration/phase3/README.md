# Phase 3 — Edge strangler, identity bridge, and OFBiz anti-corruption layer

## Status

Phase 3 completed on 2026-09-24. Repository implementation and local verification passed. The project explicitly waived Phase 3 Azure/Entra deployment evidence on 2026-09-24; the waiver is recorded rather than treating unexecuted cloud tests as successful evidence.

## Increment 1: browser boundary and governed test route

- The reviewed route manifest remains the only source allowed to mark a route as modern and now directs browser traffic to a same-origin shell BFF.
- The shell BFF exposes only the platform test use case. It validates the public host, bounds concurrency and upstream time, propagates a sanitized correlation ID, emits defensive response headers, and returns sanitized errors.
- Browser code derives the BFF endpoint from the reviewed manifest instead of hard-coding an internal service route.
- Local development routes the browser through `shell -> shell-bff -> platform-sample`; it does not expose the sample service directly to browser code.

This increment deliberately does not claim identity completion. Entra authorization-code/PKCE, server-side sessions, CSRF for state-changing requests, Party/UserLogin mapping, short-lived legacy exchange, Front Door legacy-default routing, and the narrow OFBiz adapter remain required.

## Increment 2: OIDC session and route authorization

- The BFF uses authorization code with PKCE against standards-based OIDC endpoints; local Keycloak stands in for Entra without changing the browser flow.
- Access tokens remain server-side. The browser receives an opaque HttpOnly, SameSite session cookie and never stores a bearer token.
- The platform route requires an authenticated session and the reviewed `development-user` role.
- Logout is POST-only and requires a per-session CSRF token. Login state is single-use and expires after five minutes; return paths are constrained to local absolute paths.
- Structured login/logout audit events contain the immutable provider subject, event, and outcome without tokens or credentials.

The current in-memory session store is suitable only for the local/repository gate. Azure deployment must configure a shared, encrypted server-side session store before horizontal scaling or production use.

## Increment 3: legacy bridge, adapter, edge, and fallback

- Identity Access owns explicit immutable-subject to principal/Party/UserLogin mappings and issues 30-second single-use exchange codes.
- The narrow OFBiz adapter atomically redeems a code, creates a 30-second OFBiz-scoped JWT, and establishes a normal proxied OFBiz session without receiving or copying a password.
- OFBiz internal JWT login is enabled only by explicit bridge configuration. The exchange key is external configuration and must be stored in Key Vault outside local development.
- Adapter calls are bounded by timeouts and a bulkhead; repeated failures open a short circuit. Errors are sanitized and correlation is propagated.
- Front Door routes only reviewed modern assets, BFF, auth, and exchange paths to the shell origin. The unmatched `/*` route remains the OFBiz fallback.
- Stable subject-based cohorts and an emergency kill switch control the modern test route. See [fallback-runbook.md](fallback-runbook.md).
- Security analysis is recorded in [threat-model.md](threat-model.md); the cloud-evidence waiver is recorded in [azure-acceptance.md](azure-acceptance.md).

## Completion status

Phase 3 is complete by explicit project decision. This does not claim Entra, Azure Front Door, origin isolation, distributed tracing, or cloud fallback was exercised. Any later production authorization must independently accept the residual risks in the threat model and the waived checks.

## Safety invariant

The platform test endpoint is read-only. No identity headers supplied by a browser are trusted or forwarded. Delegated legacy access must be implemented server-side before any protected or state-changing legacy capability is added.
