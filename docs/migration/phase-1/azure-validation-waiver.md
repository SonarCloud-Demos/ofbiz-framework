# Azure validation waiver

## Decision

On 2026-09-23, the migration sponsor waived Azure deployment and Azure-specific
recovery validation from the Phase 1 exit gate because no Azure infrastructure
is available to the program.

## Waived evidence

- Terraform plan/apply against development and staging subscriptions;
- verification of Azure private endpoints, private DNS and network isolation;
- managed-identity and Azure RBAC execution tests;
- Azure Monitor alert delivery and Application Insights telemetry checks;
- Container Apps scaling, canary and revision rollback;
- Azure Database for PostgreSQL point-in-time restore;
- Azure Service Bus dead-letter replay;
- Front Door/APIM routing and regional-recovery exercises;
- Azure drift detection against deployed resources.

## Evidence retained

- Terraform formatting and provider-schema validation for bootstrap, edge,
  development and staging configurations;
- digest-pinned local hybrid stack with successful modern and legacy smoke
  checks;
- federated delivery, recovery and operations procedures ready for future use.

## Consequences

The Phase 1 repository baseline may be accepted without deployed Azure proof.
No document, dashboard or release may describe Azure infrastructure as deployed,
tested, secure, recoverable or production-ready. Before any Azure environment
handles users or business data, the waived checks become mandatory release
gates and must be recorded against the actual environment.

| Field | Value |
| --- | --- |
| Decision | Waive Azure-specific Phase 1 validation |
| Date | 2026-09-23 |
| Reason | Azure infrastructure is not available |
| Approver | Migration sponsor; identity not recorded |
| Azure deployment status | Not deployed |
