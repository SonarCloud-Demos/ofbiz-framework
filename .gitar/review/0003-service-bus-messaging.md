# ADR 0003: Azure Service Bus for asynchronous integration

- Status: Accepted 2026-09-24
- Owners/approvers: Architecture, Platform, SRE, Security — unassigned

## Decision

Use Service Bus topics/subscriptions for integration events and queues for asynchronous commands. Require transactional outbox/inbox, idempotent consumers, explicit ordering/session needs, DLQs, replay runbooks and versioned schemas. Do not claim exactly-once delivery.

## Required input

Measure event rate, size, retention, ordering, fan-out, peak/backlog recovery, availability and geo-recovery needs before selecting Standard versus Premium and namespace topology.
