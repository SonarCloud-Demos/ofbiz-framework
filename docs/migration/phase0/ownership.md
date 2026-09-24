# Ownership and on-call register

Status: responsibility model approved on 2026-09-24. Named assignments remain mandatory before the associated implementation or on-call responsibility begins.

| Area | Accountable responsibilities | Required named owner | Current assignment |
| --- | --- | --- | --- |
| Migration program | outcomes, funding, scope, phase-gate approval | Executive/product sponsor | Unassigned |
| Architecture | boundaries, ADRs, dependency exceptions | Principal architect/architecture group | Unassigned |
| Platform | Azure/Terraform, CI/CD, developer platform, cost | Platform engineering lead | Unassigned |
| Legacy OFBiz | behavior, incidents, adapter and retirement | OFBiz engineering lead | Unassigned |
| First vertical slice | service, data, UI/BFF, cutover and runbook | Stream-aligned engineering lead | Unassigned |
| Product | user journeys, priority and acceptance | Product owner | Unassigned |
| Data | ownership, migration, quality, retention | Data owner/steward | Unassigned |
| Security/privacy | threat model, identity, classification, compliance | Security/privacy owner | Unassigned |
| Reliability | SLOs, capacity, recovery, on-call readiness | SRE/operations owner | Unassigned |
| Finance | Azure cost envelope and variance | FinOps/finance owner | Unassigned |

The first slice cannot enter implementation without a named engineering owner, product owner, data owner, security reviewer, SRE reviewer, primary on-call rotation and escalation route. “The migration team” is not a valid owner. Ownership metadata must be maintained with the deployable and linked from the migration ledger.
