# Phase 2 exit-gate evidence

## Repository implementation

| Requirement | State | Evidence |
| --- | --- | --- |
| Front Door Premium and WAF | Implemented as Terraform | Edge module with prevention-mode managed WAF rules |
| Common edge and route catalog | Implemented locally | Fail-safe legacy default, explicit modern/BFF/OAuth routes |
| BFF authentication | Implemented and unit/integration tested | Authorization code with PKCE, server-side session and secure cookie settings |
| Authorization and tenancy | Implemented and tested where environment-independent | signed-role mapping, route role checks, exact issuer and tenant allowlist |
| Logout, renewal and expiry | Logout/session expiry configured; real-provider renewal exercise pending | 30-minute session, CSRF-protected logout; Entra exercise required |
| Legacy identity bridge | BFF/edge producer implemented; OFBiz verifier pending | internal-only assertion subrequest, signed 60-second audience-bound JWT |
| Shell/design baseline | Implemented | navigation, route manifest, visible marker, session/error/logout states, CSP and focus styling |
| Trusted classification | Implemented and locally tested | inbound/upstream stripping and edge-owned response values |
| Synthetic security tests | Implemented | PKCE, CSRF, authorization, signed assertion, target/marker/spoof checks |

## Waived external evidence

- deploy the Terraform edge and BFF to a non-production Azure environment;
- validate Front Door/APIM origin restriction, WAF, TLS, client IP and trusted
  classification from outside the origin network;
- implement and test the OFBiz assertion verifier including one-time `jti`
  replay prevention and the approved Entra-to-OFBiz user mapping;
- exercise sign-in, sign-out, renewal, expiry, revoked access, tenant isolation
  and role changes with the real Entra registration;
- complete route parity/performance validation for representative legacy
  routes, uploads, redirects and cookies;
- complete penetration/security and accessibility testing;
- roll internal users and then all users through the edge, measure baseline
  tolerance, and exercise direct-legacy rollback.

The migration sponsor waived every item above on 2026-09-24, including the
unimplemented OFBiz assertion consumer. See
[remaining-evidence-waiver.md](remaining-evidence-waiver.md).

Phase 2 repository implementation is complete and the Phase 2 exit gate is
**complete by sponsor waiver**. Production readiness is not established.
