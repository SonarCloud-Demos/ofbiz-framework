# ADR 0001: Azure Container Apps as the initial compute plane

- Status: Accepted 2026-09-24
- Owners/approvers: Platform, Architecture, Security, SRE — unassigned

## Decision

Run independently deployable services, BFFs and applicable jobs on Azure Container Apps. Use Container Apps Jobs for scheduled/event work where continuous hosting is unnecessary. Do not introduce AKS unless measured workload, networking, policy or operational requirements cannot be met.

## Rationale and consequences

This minimizes platform operations while retaining independent images, identities, scaling and revisions. Workloads must fit Container Apps ingress, networking, execution and scaling constraints. A representative service must prove private networking, identity, telemetry, deployment rollback, cold-start and scaling behavior before acceptance.
