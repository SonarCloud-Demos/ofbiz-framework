# Phase 1 — paved road and Azure landing zone

## Status

Phase 1 started on 2026-09-23. Work is being delivered in independently
verifiable increments. The migration sponsor authorized Phase 1 platform work
under the conditional risk acceptance in
[phase-1-authorization.md](../phase-0/phase-1-authorization.md). Phase 0
governance and production-evidence gaps remain open; the authorization does not
approve a production pilot or represent the Phase 0 exit gate as passed.

On 2026-09-23, the migration sponsor waived Azure deployment and Azure recovery
validation because the program does not currently have Azure infrastructure.
Terraform remains statically validated and retained as a future implementation
baseline. The waiver does not represent any Azure resource as deployed or
production-ready; see [azure-validation-waiver.md](azure-validation-waiver.md).

The sponsor subsequently waived the remaining clean-checkout CI, SBOM/scan and
named-owner evidence. Phase 1 is therefore **complete by sponsor waiver** as of
2026-09-23. This is an administrative gate decision, not a claim of production
readiness. See
[remaining-evidence-waiver.md](remaining-evidence-waiver.md).

## Delivery increments

| Increment | State | Evidence |
| --- | --- | --- |
| Monorepo paved-road skeleton | Implemented | Service and web-route templates, contract registry, ownership rules, architecture checks, root orchestration |
| Hybrid local runtime | Implemented and smoke-tested | Compose topology runs OFBiz, same-origin edge, modern service/route, PostgreSQL and telemetry; modern and legacy generation checks pass |
| Azure Terraform modules | Implemented; static validation pending final pass | Bootstrap, network, identity, compute, data, messaging, private endpoints, edge and observability |
| Development environment | Defined; deployment validation waived | Environment composition includes private connectivity, managed identity, backup and alerts |
| Staging environment | Defined; deployment validation waived | Isolated composition retained for future use; no Azure resources exist |
| CI/CD supply chain | Implemented; execution proof waived | Validation, federated plan/apply, drift, SBOM, scan, signing, attestation and digest promotion workflows exist |
| Operational readiness | Documented; Azure exercises waived | SLO/telemetry conventions and recovery runbooks exist; Azure game days require future infrastructure |

## First-increment acceptance criteria

- `./modern check` validates repository structure, contracts, forbidden service
  dependencies, and Compose configuration without starting containers.
- `./modern build` and `./modern test` invoke every registered paved-road
  component and fail when its prerequisites are unavailable.
- `./modern up`, `down`, `reset`, and `smoke` have stable semantics and never
  delete local data unless `reset` is explicitly confirmed.
- The template service is a standalone Java 21/Spring Boot build with health,
  readiness, structured logging, OpenAPI, tests, and no dependency on OFBiz.
- The template route is a separately testable static artifact suitable for a
  future shell feature route.
- Registry coordinates and credentials are injected. No public fallback or
  credential is committed.

## Important limitations

This increment is scaffolding, not an operational Phase 1 exit. It does not
yet prove Azure deployment, private networking, managed identity, backups,
alerts, canary rollback, disaster recovery, or production readiness. The
Compose topology intentionally requires organization-approved image
coordinates rather than silently pulling public images.

## Usage

See [paved-road.md](paved-road.md) for prerequisites and commands. The Phase 1
exit gate remains the one defined in
[OFBIZ_microservices-plan.md](../OFBIZ_microservices-plan.md).
Current evidence and the final gate disposition are tracked in
[exit-gate.md](exit-gate.md).
