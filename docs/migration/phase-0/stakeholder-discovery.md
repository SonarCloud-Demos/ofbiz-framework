# Phase 0 stakeholder and evidence backlog

## Purpose

Source code describes possible behavior; it cannot establish deployed customizations, business criticality, production usage, legal obligations, user needs, or operational targets. This backlog lists the minimum evidence and decisions needed to pass Phase 0 without storing secrets or production data in Git.

Record approved conclusions in ADRs or sanitized migration documents. Keep raw logs, database profiles, architecture diagrams containing sensitive endpoints, contracts, and security evidence in the organization's approved restricted system.

## Required participants and accountabilities

| Role | Accountable Phase 0 decisions |
| --- | --- |
| Executive/program sponsor | Scope, funding, business outcomes, risk acceptance, decommission intent |
| Product owners | Journey priorities, functional equivalence, rollout cohorts, user communication |
| Domain experts/OFBiz maintainers | Invariants, hidden behavior, ECAs/jobs, exceptions, terminology |
| Data owners/stewards | Ownership, quality, classification, retention, migration/reconciliation sign-off |
| Security/privacy/compliance | Identity, authorization, tenant isolation, threat model, regulatory controls |
| Operations/SRE/support | SLOs, incidents, observability, support hours, RTO/RPO, rollback authority |
| Finance/accounting control owners | Financial invariants, audit, period close, parallel-run acceptance |
| Integration owners | Partner contracts, certificates, change windows, sandbox/support procedures |
| Platform/cloud team | Azure landing-zone constraints, Terraform/CI identity, network and cost standards |
| UX/accessibility | User research, navigation, design system, marker behavior, accessibility target |

Every capability and pilot must have named individuals for product, domain, data, engineering, and operations ownership.

## Evidence requests

### Deployed system

- exact Git revision, plugin/custom component list, patches, build flags, and configuration override inventory per environment;
- topology, host/process counts, database engine/version, reverse proxies, certificates, DNS, firewall/allowlist dependencies, file shares, and batch hosts;
- release pipeline, approval model, rollback process, maintenance windows, environment drift, and last restore test;
- license/support constraints and upstream OFBiz update strategy during migration.

### Users, routes, and authorization

- user populations and identity sources: employees, partners, customers, service accounts, batch identities;
- tenant/legal-entity/organization model and how users are scoped to data;
- role/group/permission exports with personally identifying fields removed;
- 30–90 representative days of aggregated route counts, latency, response status, payload size, and user-role/tenant class;
- critical user journeys, peak calendars, accessibility needs, localization/time-zone requirements, and browser/client support;
- inactive routes and privileged administration routes that should be retired rather than migrated.

### Data

- per-table row counts, allocated size, change rates, key ranges, and growth without row contents;
- duplicate/orphan/null/invalid-status profiles for proposed pilot data;
- authoritative system and data steward for each major subject area;
- PII, financial, payment, employee, credential, content, and audit classifications;
- residency, encryption, retention, legal hold, erasure, export, masking, and lower-environment rules;
- current tenant partitioning and backup/restore granularity;
- approved reconciliation invariants and sign-off authority.

### Behavior and integration

- dispatcher-level service call graph for representative journeys, including duration, nesting, transaction, async and failure aggregates;
- ECA firing and scheduled-job history with owner, deadline, parameters, retries, overlaps, and outcomes;
- inbound/outbound integration register with contracts and non-secret authentication descriptions;
- report catalog, recipients, data sources, timing, regulatory status, and manual spreadsheet/rekeying workflows;
- email/SMS, payment, tax, shipping/carrier, bank, FTP/file, calendar, content/media, search/index, and partner integrations;
- event/order guarantees implicitly relied upon today.

### Reliability and performance

- current availability/error/latency objectives and actuals by critical journey;
- traffic peaks, long-running requests, job windows, database connection/lock/slow-query metrics;
- incident and problem records, common support cases, known data repairs, and capacity constraints;
- RTO/RPO, backup retention, restore results, disaster-recovery exercises, and dependency recovery ordering;
- current infrastructure/license/operations cost baseline and Azure budget guardrails.

## Workshops

### 1. Business journey and value workshop

Output: ranked journeys with actors, triggers, successful outcome, frequency, business value, failure impact, seasonality, and product owner. Mark retire/retain/migrate and select pilot candidates.

### 2. Event storming by value stream

Output: commands, domain events, policies, aggregates/invariants, hot spots, external systems, read models, and candidate context boundaries. Run separate sessions for catalog-to-order, order-to-cash, procure/stock-to-fulfillment, record-to-report, and workforce/manufacturing as applicable.

### 3. Data ownership and classification

Output: system of record at each migration stage, stewardship, sharing purpose, classification, retention, residency, deletion/export, reconciliation, and prohibited replication.

### 4. Identity, authorization, and threat modeling

Output: actors/identity sources, tenant boundary, authentication assurance, role/attribute rules, privileged access, service identities, audit events, trust boundaries, abuse cases, and mitigations. Include the temporary Entra-to-OFBiz identity bridge.

### 5. Operations and resilience

Output: service tiers, SLO/error budget, support model, observability, batch deadlines, RTO/RPO, backup/restore, rollback authority, incident communication, and dependency failure behavior.

### 6. Pilot definition

Output: one-page journey, in/out scope, UI routes, APIs/data, authorization matrix, invariants, golden data, SLO, telemetry, cohorts, acceptance, rollback triggers, owners, and removal criteria.

## Decision register

Create ADRs for decisions only after evidence and responsible-owner review. Initial queue:

| Decision | Status | Required input |
| --- | --- | --- |
| Provisional product search/detail pilot | Proposed | Usage, owner/value, data/authorization rules, volumes/SLO |
| Bounded-context map | Hypothesis | Event storming and ownership workshops |
| Azure Container Apps rather than AKS | Proposed | Landing-zone standards, workload requirements, operations skills |
| Front Door Premium + APIM edge | Proposed | Threat/network model, traffic, cost, routing needs |
| Entra ID + BFF and legacy identity bridge | Proposed | User populations, tenant model, assurance and session requirements |
| PostgreSQL service databases | Proposed | Data features, HA/DR, scale, residency, operational standards |
| Azure Service Bus | Proposed | Delivery/order/replay/throughput requirements |
| Modern UI route modules before micro-frontends | Proposed | Team/release topology, UX and caching requirements |
| Service runtime/language baseline | Proposed | Organization standards, skills, support lifecycle |

## Security and privacy discovery checklist

- [ ] Trust boundaries and data flows documented for current and transition states.
- [ ] All human and machine identity types cataloged.
- [ ] Tenant boundary and cross-tenant administration rules documented.
- [ ] Privileged operations and break-glass process documented.
- [ ] Authentication assurance, MFA, Conditional Access, session and logout requirements approved.
- [ ] Authorization matrix created per pilot route/API/action/field.
- [ ] Sensitive data classified, minimized, and prohibited log fields defined.
- [ ] Encryption, key ownership/rotation, secrets, certificates, and partner trust documented.
- [ ] Audit event schema, retention, access, and tamper resistance approved.
- [ ] Regulatory/payment/employee/privacy obligations and evidence owners recorded.
- [ ] Threat model covers edge, BFF, identity bridge, adapters, messaging, data sync, admin/support, CI/CD and Terraform.
- [ ] Penetration, dependency, container, IaC, SAST/DAST and remediation gates agreed.

## Pilot authorization matrix template

| Actor/role | Search | Basic detail | Sensitive field set | Tenant/store scope | Audit requirement |
| --- | --- | --- | --- | --- | --- |
| Catalog viewer | TBD | TBD | Deny unless justified | TBD | TBD |
| Catalog administrator | TBD | TBD | TBD | TBD | TBD |
| Support operator | TBD | TBD | Masked by default | Explicit elevation | Required |
| Service identity | Least required | Least required | Deny by default | Explicit audience/tenant | Required |

## Phase 0 completion record

When the evidence and workshops are complete, update `README.md` with links to the sanitized decisions, baseline date range, named ownership location, selected pilot, and exit-gate approvals. Do not mark Phase 0 complete merely because platform implementation is ready to begin.
