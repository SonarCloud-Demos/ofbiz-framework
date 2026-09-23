# Phase 0 — discovery and boundary validation

## Status

Phase 0 started on 2026-09-23. The repository-level static discovery is complete enough to establish the initial migration hypotheses and choose a provisional pilot. Runtime evidence, production characteristics, and stakeholder decisions are still outstanding; Phase 0 has **not** passed its exit gate.

This folder is the working record for Phase 0:

- [static-inventory.md](static-inventory.md) records migration-relevant assets and coupling visible in the tracked source tree;
- [context-map-and-pilot.md](context-map-and-pilot.md) proposes boundaries and scores the first vertical slice;
- [stakeholder-discovery.md](stakeholder-discovery.md) is the interview/workshop and evidence backlog needed to finish the phase.

The target and phased migration approach remain in [OFBIZ_microservices-plan.md](../OFBIZ_microservices-plan.md). The current-state narrative is in [OFBIZ_current_architecture.md](../OFBIZ_current_architecture.md).

## Phase 0 outcomes

Phase 0 must produce these approved outcomes before Phase 1 implementation begins:

| Outcome | Current state | Completion evidence |
| --- | --- | --- |
| Route, service, data, ECA, job, report, and integration inventory | Static baseline complete; runtime usage unknown | Inventory reviewed by application owners and enriched with production traces/volumes |
| Bounded-context map | Working hypothesis documented | Domain workshop decisions and ADRs approved by named owners |
| First thin vertical slice | Provisional product catalog search/basic detail pilot | Product owner, security, operations, and data owner approve scope and invariants |
| Data classification and compliance | Source-informed hypotheses only | Formal classification, retention, residency, privacy, audit, and deletion requirements |
| Non-functional baseline | Not available from repository | Measured latency, throughput, availability, batch windows, RTO/RPO, and costs |
| Security baseline and threat model | Initial threat list documented | Identity/tenant model and threat model reviewed by security |
| Program scorecard | Proposed below | Baseline values collected and target thresholds approved |
| Ownership and governance | Unknown | Named domain, product, platform, data, security, operations, and migration owners |

## Working assumptions

These assumptions allow discovery to progress but require confirmation:

- the tracked repository represents the deployed application baseline, with production plugins and customizations to be supplied separately;
- the first migration is for the back-office catalog UI mounted at `/catalog`, not an optional ecommerce plugin;
- production has one authoritative OFBiz database per deployment/tenant arrangement;
- preservation of OFBiz identifiers during coexistence is acceptable;
- Azure Container Apps, PostgreSQL, Service Bus, Entra ID, Front Door, and APIM remain hypotheses until ADR approval;
- no production service-level objectives, regulatory classifications, or traffic/data volumes can be inferred safely from source code.

If any assumption is false, update the inventory and pilot score before implementation.

## Initial findings

1. **The database model is more centralized than the source modules.** `applications/datamodel` declares 733 of the 846 counted concrete entities and 160 cross-domain view entities. Extracting an application directory is not equivalent to extracting its data.
2. **The behavioral surface is large and metadata-driven.** Approximately 3,912 service declarations, 3,127 controller request maps, 330 Service ECAs, and 35 Entity ECAs need both static and runtime analysis.
3. **Product is representative but not small.** The product component has the largest application route and service counts. The pilot must be a deliberately narrow read-only journey, not “extract product.”
4. **Implicit execution is material.** Scheduled data exists in framework, accounting, order, product, and manufacturing; ECAs can create paths invisible in Java call graphs.
5. **Existing remote service engines are not bounded-context APIs.** SOAP, HTTP, JMS, RMI, and route engine declarations exist, but the standard deployment remains an in-process shared model. Each production use must be verified.
6. **Authorization starts with coarse application permissions but continues in code/services.** Manifest permissions such as `CATALOG`, `ORDERMGR`, and `ACCOUNTING` are only the first layer of the authorization inventory.

## Proposed program scorecard

Collect a baseline over at least one representative business cycle, including month end and major batch windows where applicable.

| Measure | Baseline | Initial target/decision needed | Source |
| --- | --- | --- | --- |
| Production requests by OFBiz route | TBD | 100% classified by owner and criticality | Edge/access logs plus controller catalog |
| Active OFBiz services | TBD | 100% of observed calls mapped to a context | OpenTelemetry instrumentation/service dispatcher logs |
| Active ECAs and scheduled jobs | TBD | Owner and disposition for every production-active rule/job | Runtime logs, `JobSandbox`, source inventory |
| P50/P95/P99 latency by critical journey | TBD | Product SLO per journey | Synthetic and real-user/API telemetry |
| Availability and error rate | TBD | Product SLO and error budget | Monitoring/incident records |
| Peak request and job throughput | TBD | Capacity target plus growth factor | Access logs, DB/job metrics |
| Database size/growth and largest tables | TBD | Migration/backfill windows and retention | Production database metadata |
| Reconciliation mismatch rate | Not yet applicable | Zero for financial invariants; tolerance per non-financial field | Migration reconciliation jobs |
| Deployment frequency and lead time | TBD | Improvement target | CI/CD records |
| Change failure rate and MTTR | TBD | Improvement target | Deployment/incident records |
| Route generation (`legacy/hybrid/modern`) | 100% legacy initially | Tracked per release and production request | Future trusted route catalog/telemetry |
| Infrastructure and license cost | TBD | Budget/transaction targets | Azure forecast and current cost reports |
| Accessibility and user task success | TBD | WCAG target and journey threshold | Audits and usability tests |

## Exit-gate checklist

- [x] Repository static inventory and coupling baseline recorded.
- [x] Working bounded-context hypothesis recorded.
- [x] Provisional pilot and alternatives scored.
- [ ] Production plugins/custom components and deployed revision supplied and inventoried.
- [ ] Route and service runtime traces captured for representative periods.
- [ ] Production data volumes, quality, growth, and tenant topology profiled safely.
- [ ] External integrations, reports, scheduled jobs, and operational procedures validated with owners.
- [ ] Domain event-storming/context-mapping workshops completed.
- [ ] Data classification, regulatory, residency, retention, audit, and privacy decisions approved.
- [ ] Identity, user population, tenant isolation, and authorization model approved.
- [ ] SLO, RTO/RPO, capacity, cost, and support baselines approved.
- [ ] Pilot journey, invariants, acceptance criteria, and rollback authority approved.
- [ ] Named product/domain/platform/data/security/operations owners recorded.
- [ ] ADRs created for approved target-platform choices.

## Next working session

The next session should combine the product owner, an OFBiz catalog maintainer, a data owner, security, operations, and the migration architect. It should validate the product-search/detail journey against real user behavior, complete its authorization matrix and data set, and decide whether it remains the pilot. In parallel, operations should begin the non-sensitive production evidence capture described in `stakeholder-discovery.md`.
