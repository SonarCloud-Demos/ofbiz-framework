# ADR 0008: Single-region, zone-redundant baseline pending recovery requirements

- Status: Accepted 2026-09-24; detailed RPO/RTO, residency and recovery topology remain production inputs
- Owners/approvers: Product, SRE, Data, Security/Compliance, Finance — unassigned

## Decision

Start with one approved Azure region per environment and zone-redundant production services where supported. Use tested backups/point-in-time restore, Terraform recreation and documented regional recovery. Do not assume active/active multi-region.

## Required input

Approve availability SLO, RPO, RTO, maximum cutover downtime, data residency, dependency regional availability and cost envelope for each critical tier. Select paired/secondary region and failover mechanism only from those requirements, then run recovery exercises.
