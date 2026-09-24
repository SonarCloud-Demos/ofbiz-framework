# Requirements and production baseline register

Status: framework and deferral of measured values approved on 2026-09-24. Slice-specific values remain mandatory before production cutover.

## Program success measures

| Measure | Baseline window/value | Target | Source | Accountable owner | Status |
| --- | --- | --- | --- | --- | --- |
| Deployment frequency | Not measured | Pending | CI/release records | Unassigned | Open |
| Change lead time | Not measured | Pending | VCS/CI/deployment records | Unassigned | Open |
| Change failure rate | Not measured | Pending | Deployments/incidents | Unassigned | Open |
| Mean time to restore | Not measured | Pending | Incident records | Unassigned | Open |
| Escaped defect rate | Not measured | Pending | Defect tracker | Unassigned | Open |
| Availability by critical journey | Not measured | Pending | Synthetic/edge telemetry | Unassigned | Open |
| p50/p95/p99 journey latency | Not measured | Pending | Traces/real-user monitoring | Unassigned | Open |
| Peak/seasonal throughput | Not measured | Pending | Edge/service/database telemetry | Unassigned | Open |
| Azure monthly cost envelope | No Azure target estate | Pending | Finance/forecast | Unassigned | Open |

Use at least one representative normal window and one known peak/seasonal window. Record time range, timezone, exclusions, sample size, query/dashboard version, and data-quality caveats.

## Capability requirements template

Complete one reviewed row per accepted slice before its charter is approved.

| Capability/slice | Availability tier/SLO | Latency SLO | RPO | RTO | Cutover downtime | Volume/peak | Retention/deletion | Residency | Privacy/payment/audit obligations | Approvers | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Notification delivery pilot | Pending | Pending | Pending | Pending | Pending | Pending | Pending | Pending | recipient/content classification; consent/audit pending | Unassigned | Open |
| Catalog browse candidate | Pending | Pending | Pending | Pending | Pending | Pending | Pending | Pending | public/internal fields pending | Unassigned | Open |
| Party candidate | Pending | Pending | Pending | Pending | Pending | Pending | Pending | Pending | personal data/privacy rights pending | Unassigned | Open |
| Inventory candidate | Pending | Pending | Pending | Pending | Pending | Pending | Pending | Pending | commercial/audit requirements pending | Unassigned | Open |

## Classification policy to approve

Every field/event/log/backup is classified as Public, Internal, Confidential, or Restricted. Restricted includes credentials/tokens, payment data, sensitive HR data and any category designated by compliance. Owners must define collection purpose, lawful basis where relevant, field minimization, encryption, access, logging/redaction, retention, deletion/legal hold, backup disposition, residency and breach/audit obligations.

Data classification is owned by business/data/privacy stakeholders, not inferred from an OFBiz table or this document. Until approved, treat identity, party, payment, HR and message content as Restricted and deny broad telemetry/export.

## Production trace baseline

Required trace attributes: timestamp, environment, normalized route, OFBiz component, service name, entity name and operation, transaction/correlation ID, duration, outcome, caller/callee relationship and scheduled-job identifier. Aggregate counts and percentiles before committing evidence. Exclude payloads and values. Security/privacy must approve collection and retention.
