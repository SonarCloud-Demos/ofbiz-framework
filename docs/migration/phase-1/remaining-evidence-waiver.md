# Remaining Phase 1 evidence waiver

## Decision

On 2026-09-23, the migration sponsor waived the remaining non-Azure evidence
from the Phase 1 exit gate:

- clean-checkout execution of the complete build, test and smoke flow in CI;
- retained SBOM and dependency/container-image scan results;
- named platform, security and operations owners accepting the paved road.

Together with the separate
[Azure validation waiver](azure-validation-waiver.md), this closes the Phase 1
exit gate by sponsor waiver.

## Evidence that does exist

- the hybrid stack built and started locally;
- service unit tests passed during the container build through Artifactory;
- modern readiness, API, visible marker and generation-header checks passed;
- the legacy OFBiz route and legacy generation-header check passed;
- Terraform bootstrap, platform, edge, development and staging configurations
  passed formatting and provider-schema validation;
- repository architecture and whitespace checks passed.

## Consequences

Phase 1 is administratively complete, not fully assurance-tested. The waiver
does not claim that CI reproducibility, vulnerability posture, software-bill-of-
materials retention, supply-chain attestations, or operational ownership were
verified. These controls become mandatory before a production deployment or
before an environment processes real user or business data.

| Field | Value |
| --- | --- |
| Decision | Waive all remaining Phase 1 exit evidence |
| Date | 2026-09-23 |
| Approver | Migration sponsor; identity not recorded |
| Phase 1 status | Complete by sponsor waiver |
| Production readiness | Not established |
