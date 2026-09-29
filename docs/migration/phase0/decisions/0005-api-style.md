# ADR 0005: HTTP/OpenAPI plus versioned integration events

- Status: Accepted 2026-09-24
- Owners/approvers: Architecture and service owners — unassigned

## Decision

Use private HTTP/JSON APIs described by OpenAPI for immediate decisions and queries. Use versioned events/commands for propagation and long-running workflows. Mutations are idempotent, synchronous calls have timeouts, and compatibility is CI-tested. Avoid deep call chains and distributed joins.

## Consequences

BFFs compose UI responses; services maintain local projections. Exceptions such as streaming or bulk transfer require a separate ADR with measured need.
