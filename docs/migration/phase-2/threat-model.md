# Phase 2 identity and edge threat model

## Assets and trust boundaries

The public trust boundary is Front Door/WAF. APIM and the local reference edge
select a route owner and overwrite trusted forwarding/classification headers.
The shell BFF is the browser identity boundary: OAuth tokens remain in its
server-side session. OFBiz trusts only assertions produced by the BFF and
forwarded through the edge's internal subrequest.

## Required controls

| Threat | Repository control | Environment evidence still required |
| --- | --- | --- |
| Client spoofs route generation | Edge removes inbound/upstream values and sets classification after route selection | External probe through Front Door/APIM |
| Client spoofs identity | Edge removes `X-Legacy-Identity-Assertion`; internal-only auth subrequest obtains it from BFF | OFBiz verifier test and replay attempt |
| Authorization-code interception | Authorization request uses PKCE; redirect URI is fixed by client registration | Entra sign-in trace with approved registration |
| Token theft from browser | Tokens remain in server-side session; cookie is `Secure`, `HttpOnly`, `SameSite=Strict` | Browser storage/cookie inspection |
| Login/session fixation | Spring Security migrates the session after authentication | Interactive login test |
| Cross-site request forgery | Unsafe BFF operations require a repository-issued CSRF token | Browser-origin negative tests |
| Cross-tenant access | Exact issuer and tenant allowlist are checked when loading the OIDC user | Two-tenant negative test with real Entra tokens |
| Excess privilege | Legacy assertion requires `LEGACY_USER`; roles come from signed ID-token claims | Entra role assignment and OFBiz mapping review |
| Assertion replay | Assertion is audience-bound, has a unique `jti`, and expires after 60 seconds | OFBiz one-time `jti` cache implementation and replay test |
| Clickjacking/XSS | CSP denies framing, objects and non-self scripts; output uses DOM text APIs | CSP report review and penetration test |
| Host/client-IP spoofing | Local edge rebuilds forwarded fields from the direct peer | Front Door origin restriction and known-proxy validation |

## Waived security item

OFBiz would need to validate assertion signature, issuer, audience, expiry and one-time
`jti` use before enabling the bridge outside a controlled test environment. A
browser-supplied identity header must never be accepted, even if it has the
same name as the edge-to-OFBiz header. This implementation and its evidence were
waived for the Phase 2 gate; the bridge therefore remains unsuitable for real
users or business data.
