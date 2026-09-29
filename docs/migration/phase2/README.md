# Phase 2 — Azure landing zone and delivery platform

## Recorded infrastructure constraint

As of 2026-09-24, the project has no Azure subscription or Azure infrastructure available for deployment or integration testing. The team has therefore completed Phase 2 as a **repository implementation gate**: all infrastructure, policy, delivery, rollback, ephemeral-environment, and recovery procedures that can be authored and checked without Azure are present and locally verified. Azure-backed exit evidence is explicitly deferred, not represented as successful.

## Repository baseline

- `infra/bootstrap` creates recoverable Azure Blob remote state with leases, versioning, soft delete, RBAC, and Azure AD authentication.
- Dev and staging are independent Terraform roots and state keys. Reviewed modules cover resource groups, networking/private DNS, private data services, the sample service and shell on Container Apps, ACR, observability, APIM, complete Front Door routing/WAF association, actionable alerts, and budgets.
- CI uses GitHub OIDC federation. Pull requests format, validate, scan, and retain plans; applies are protected by GitHub environments and use the reviewed plan artifact.
- Application promotion is separate from infrastructure apply. Images are built, scanned, signed keylessly, attested with an SBOM, pushed to ACR, deployed as a new Container Apps revision, smoke-tested, and rolled back on failure.
- Production Terraform is intentionally absent until the reliability and security review approves it.
- Pull requests labelled `azure-ephemeral` have a separately keyed, budgeted, two-day environment definition with close and scheduled TTL cleanup.
- Mocked Terraform tests and Gradle architecture checks enforce the security invariants without contacting Azure.

## Required repository configuration

Configure GitHub environments `azure-dev` and `azure-staging`, with required reviewers for staging. Set environment variables `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`, state backend coordinates, alert email, ACR name, and Container App resource group/environment. Azure federated credentials must restrict `sub` to the corresponding repository environment. Grant the plan identity read access plus state RBAC; grant the apply identity only the roles required by these modules.

Database bootstrap credentials must be supplied from the protected environment at plan/apply time and rotated into Key Vault after provisioning. They must never be committed or stored in a plan artifact with broad access.

## Repository completion status

The locally executable Phase 2 scope is complete. Formatting, provider validation, mocked Terraform tests, workflow parsing, security assertions, and Gradle registration checks are the repository evidence. Recovery procedures are in [recovery-runbook.md](recovery-runbook.md), and the eventual Azure acceptance procedure is in [azure-acceptance.md](azure-acceptance.md).

## Deferred Azure acceptance gate

Phase 2 is complete only after Azure-backed evidence records reproducible dev/staging creation, private-access tests, traces and alerts, cost reporting, canary rollback, PostgreSQL backup restore, state recovery, Key Vault recovery, and full recreation. Store dated evidence and approvals here. Repository implementation alone does not satisfy those exit criteria.

This deferred gate does not block beginning Phase 3 repository work while Azure is unavailable, but it must pass before production traffic or production infrastructure is authorized.
