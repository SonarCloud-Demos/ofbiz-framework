# Phase 3 threat model

| Threat | Control | Repository evidence |
|---|---|---|
| Session fixation | A new opaque session ID is generated only after code redemption; login state is single-use and expires in five minutes. | `ShellBffApplication`, unit tests |
| Confused deputy | The route role is checked before exchange issuance; immutable OIDC subject mappings explicitly bind principal, Party, and UserLogin. | shell BFF and identity-access tests |
| Token replay | OIDC tokens stay server-side. Legacy exchange codes expire in 30 seconds and are atomically removed when redeemed. The derived OFBiz JWT expires in 30 seconds and is never returned to the browser. | identity-access and adapter tests |
| Privilege drift | Mapping is explicit and fails closed when absent; every receiving boundary applies its own role or UserLogin permission checks. Mapping changes require reviewed configuration. | local mapping fixture and authorization tests |
| Open redirect | Login and legacy return targets accept only local absolute paths and reject scheme-relative paths. | BFF and adapter unit tests |
| Legacy bypass | Front Door owns the public hostname; modern origins use private link and the legacy origin must accept traffic only from the edge path before acceptance. | Terraform edge rules; deferred Azure test |
| Header spoofing | Browser identity headers are ignored. Host and method checks run at the BFF; workload calls require a constant-time compared secret pending managed-identity deployment. | BFF and identity-access tests |
| Adapter abuse | The adapter exposes one bounded session-establishment use case, no generic entity CRUD, with timeouts, bulkhead, circuit breaker, and sanitized errors. | adapter source and OpenAPI contract |
| XSS/marker spoofing | Provider and service values are text-escaped; the marker is a reviewed manifest/build property rather than response or query data. | shell source and build assertion |

Residual risks accepted by the Phase 3 deployment-evidence waiver: managed-identity replacement of the repository/local workload token, shared encrypted session storage, Front Door origin-lock validation, Entra tenant/issuer validation, production Key Vault rotation, and distributed trace inspection. Any future production authorization must review these risks independently.
