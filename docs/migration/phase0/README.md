# Phase 0 evidence and governance

This directory implements the repository-controlled portion of migration Phase 0. It separates facts obtainable from the checkout from decisions and baselines that require production telemetry, business owners, security/compliance, SRE, and finance.

## Reproducible evidence

Run:

```text
./gradlew generatePhase0Inventory
./gradlew verifyPhase0Inventory
```

`generatePhase0Inventory` parses active OFBiz component metadata and source files and writes the committed files under `generated/`. `verifyPhase0Inventory` regenerates into `build/`, compares byte-for-byte, and is a dependency of `check`. It fails when architectural metadata changes without refreshing the baseline.

The generated evidence inventories components, entity/view definitions, entity and service ECAs, services, controller requests/views/includes, permissions, scheduled-job declarations, test suites, report candidates, integration candidates, and conservative component dependency candidates. It is static evidence, not a complete runtime call graph.

## Controlled artifacts

| Artifact | Purpose | Current status |
| --- | --- | --- |
| `generated/ofbiz-static-inventory.json` | Machine-readable static inventory and source locations | Generated and drift-checked |
| `generated/ofbiz-static-inventory-summary.md` | Human-readable component/count summary | Generated and drift-checked |
| `generated/ofbiz-component-dependency-candidates.md` | Conservative static component relationships | Generated; runtime validation required |
| `capability-map.md` | Current capabilities, candidate target contexts, criticality and evidence | Approved initial baseline |
| `context-map.md` | Current/target relationships and invariant boundaries | Approved initial baseline |
| `migration-ledger.csv` | Ownership and cutover control skeleton | Approved and initialized |
| `slice-backlog.md` | Transparent first-slice assessment | First slice approved |
| `requirements-baseline.md` | SLO, RPO/RTO, classification and compliance decision register | Framework approved; slice values follow |
| `ownership.md` | Team and on-call responsibility register | Model approved; named assignments follow |
| `characterization-baseline.md` | Existing automated coverage and missing critical journeys | Phase 0 baseline approved |
| `decisions/` | Required target-architecture decision records | Accepted 2026-09-24 |

## Phase 0 completion status

Phase 0 is **complete and approved as of 2026-09-24**. The project approval accepts the capability map, initial context map, migration ledger, first-slice selection, requirements framework, ownership model, characterization baseline, and architecture decisions.

The following items remain required implementation inputs and must be completed by the assigned delivery owners before the affected production slice is cut over, but they no longer block closure of Phase 0:

- production request/service/entity traces and traffic/volume distributions;
- measured availability, latency, error, recovery, deployment and incident baselines;
- automated browser-level critical journeys executed against production-like data;
- business validation of capability boundaries and first-slice value;
- named product, engineering, security, data, SRE, finance and on-call owners;
- approved data classification, retention, residency, privacy, payment and audit requirements;
- approved SLOs, availability tiers, RPO/RTO, downtime budgets and Azure cost envelope;
- decisions on Entra tenant/user population and regional topology.

These are deliberately not filled with invented values. Their collection and refinement continue as governed delivery work under the accepted registers in `requirements-baseline.md`, `ownership.md`, `slice-backlog.md`, and the ADRs. Phase 1 may begin.

## Evidence handling

Static candidates can contain false positives and omit dynamic calls. Validate them by collecting sanitized production traces with component, route, service name, entity operation, duration, outcome and correlation ID. Do not capture request bodies, credentials, tokens, payment data, or unrestricted personal data. Store aggregated trace results and the collection window—not raw sensitive telemetry—in the reviewed Phase 0 artifacts.
