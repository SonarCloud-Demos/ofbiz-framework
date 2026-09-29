# ADR 0004: PostgreSQL database per service

- Status: Accepted 2026-09-24
- Owners/approvers: Architecture, Data, Platform, SRE, Security — unassigned

## Decision

Each service owns a PostgreSQL database and principal. Services never share schemas or query another service database. Multiple databases may share a Flexible Server initially; sensitive/high-scale services may use dedicated servers. Physical consolidation must not weaken logical ownership.

## Required input

Measure storage, transaction rate, connections, extensions, backup/restore, RPO/RTO, residency and isolation needs. Prove Entra/managed-identity authentication or document Key Vault credential rotation.
