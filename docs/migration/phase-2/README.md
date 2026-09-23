# Phase 2 — secure strangler edge, identity, and UI shell

## Status

Phase 2 started on 2026-09-24. Repository implementation is complete. On
2026-09-24, the migration sponsor waived all remaining Azure, Entra, OFBiz
assertion-consumer, penetration-testing, rollout and external-evidence
requirements. Phase 2 is therefore **complete by sponsor waiver**. This is an
administrative gate decision, not a production-readiness claim; see
[remaining-evidence-waiver.md](remaining-evidence-waiver.md).

## Implemented increment

| Capability | State | Evidence |
| --- | --- | --- |
| Route catalog | Implemented | `platform/routes.yaml`; unknown routes default to legacy ownership |
| Common local edge | Implemented | Explicit modern targets, legacy passthrough, forwarded request metadata and trusted generation classification |
| Spoofing defense | Implemented locally | Inbound generation headers are removed; upstream generation headers are hidden; the edge sets the response value |
| Modern shell | Implemented | Source-controlled manifest, visible marker, shared navigation baseline, CSP and accessible focus/landmarks |
| Synthetic checks | Implemented | Offline policy checks plus runtime marker, target and spoofing checks in `modern smoke` |
| Entra/BFF identity | Implemented; environment validation pending | Authorization code + PKCE, server-side sessions, secure cookies, logout, CSRF and role/tenant controls |
| Legacy identity bridge | Producer implemented; OFBiz verifier pending | Internal-only signed assertion forwarding; browser identity headers are removed |
| Front Door WAF | Implemented as Terraform; deployment pending | Prevention-mode default and bot managed rules |
| Azure rollout | Not performed | Phase 1 Azure validation remains waived; there is no Azure infrastructure |

## Security properties of this increment

- The default edge target is OFBiz, so a missing catalog entry does not move a
  business route accidentally.
- Client-supplied and upstream-supplied `X-Application-Generation` values are
  discarded. The edge derives the response value from its selected route.
- The shell derives its visible marker from a checked-in manifest and refuses
  to render its main content for an unknown generation.
- Forwarded client IP is rebuilt from the direct peer in the local reference
  topology rather than trusting a browser-supplied chain.
- The shell supplies a restrictive CSP, disallows framing and objects, and does
  not store tokens in browser storage.

These controls are a repository baseline, not penetration-test evidence.

## Evidence and operations

- [Threat model](threat-model.md)
- [Route validation and rollback](route-validation.md)
- [Exit-gate evidence](exit-gate.md)
- [Remaining evidence waiver](remaining-evidence-waiver.md)

The modern services and shell participate in the root Gradle `build`, `test`,
and `sonar` lifecycles through composite builds and aggregate web tasks.

## Exit gate

The Phase 2 exit gate is complete by sponsor waiver. The missing and unvalidated
controls remain recorded in [exit-gate.md](exit-gate.md) and become mandatory
before the affected capabilities handle real users or business data.
