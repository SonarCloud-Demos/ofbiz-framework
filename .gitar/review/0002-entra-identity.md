# ADR 0002: Microsoft Entra ID for identity

- Status: Accepted 2026-09-24; workforce versus external tenant topology is an implementation parameter
- Owners/approvers: Product, Identity, Security, Privacy — unassigned

## Decision

Use Entra ID as the identity provider, authorization-code flow with PKCE for humans, managed identities for workloads, and server-side BFF sessions. Map immutable tenant/subject pairs to application principals and Party IDs. Never synchronize legacy passwords.

## Required input

Inventory employee, customer, supplier, partner and automation populations; federation/self-service requirements; tenant isolation; lifecycle/provisioning; MFA/conditional access; recovery and regulatory constraints. Select workforce or external tenant topology only after this evidence is approved.
