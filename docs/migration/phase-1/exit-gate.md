# Phase 1 exit-gate evidence

## Repository evidence

| Requirement | State | Evidence |
| --- | --- | --- |
| Service and web templates | Implemented | `services/template-service`, `web/template-route` |
| Contracts, ownership, architecture rules | Implemented | `platform/contracts`, `platform/ownership.yaml`, `platform/architecture/check.sh` |
| Root developer commands | Implemented | `modern` |
| Hybrid local topology | Implemented and locally verified on 2026-09-23 | Compose includes modern route/service, PostgreSQL, telemetry, OFBiz and same-origin edge; readiness, API, visible marker, modern header and legacy header smoke checks pass |
| Terraform bootstrap and landing-zone modules | Implemented and statically validated | State bootstrap, platform and edge modules; dev/staging compositions |
| Private connectivity and managed identity | Implemented, deployment proof pending | Private endpoints/DNS, disabled public access, workload identity and RBAC |
| Telemetry, limits, backup and alerts | Implemented, exercise pending | Application Insights, Log Analytics, replica limits, PostgreSQL retention, alerts and budgets |
| CI policy and drift contract | Implemented, hosted execution pending | `modern-phase-1.yml`, `infra/deployment-federation.md` |
| Recovery procedures | Documented, game days pending | Phase 1 recovery and operations runbooks |

## External evidence required to pass

The Azure deployment and recovery items previously listed here were waived on
2026-09-23 because no Azure infrastructure exists. See
[azure-validation-waiver.md](azure-validation-waiver.md). They become mandatory
before any future Azure environment handles users or business data.

The following non-Azure evidence was also waived by the migration sponsor on
2026-09-23:

- a clean-checkout repeat of `./modern build`, `up`, `test`, and `smoke` in CI;
- CI-produced SBOM and dependency/image scan evidence for the local artifacts;
- named platform, security and operations owners accepting the paved road.

Repository implementation cannot substitute for these environment exercises.
The Phase 1 exit gate is **complete by sponsor waiver**. Neither the Azure nor
the non-Azure waived checks are considered passed, and production readiness is
not established. See
[remaining-evidence-waiver.md](remaining-evidence-waiver.md).
