# ADR 0007: Front Door strangler edge and APIM API governance

- Status: Accepted 2026-09-24
- Owners/approvers: Platform, Security, SRE, API owners — unassigned

## Decision

Use Azure Front Door Premium as public TLS/WAF ingress and route strangler. Use APIM for approved public/partner APIs, not ordinary internal service calls. Origins are not directly public. Version-controlled route intent drives tested Terraform configuration.

## Required validation

Prove private/origin connectivity, host/header validation, session behavior, WAF/rate limits, certificate/DNS operations, route rollback and costs with the legacy and modern origins.
