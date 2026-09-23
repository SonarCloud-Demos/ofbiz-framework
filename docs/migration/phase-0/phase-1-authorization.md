# Phase 1 authorization and Phase 0 risk acceptance

## Decision

On 2026-09-23, the migration sponsor authorized the program to proceed with
Phase 1 despite outstanding Phase 0 exit-gate evidence.

The approver's personal identity and organizational role were not supplied in
the repository. They must be added before this record is treated as a formal
organizational sign-off.

## Scope of authorization

The authorization permits reversible, non-production Phase 1 platform work:

- monorepo templates, contracts, ownership rules, and architecture tests;
- local development and hybrid-stack automation;
- Terraform modules and isolated development/staging environments;
- CI/CD, supply-chain, telemetry, security, backup, recovery, and operational
  readiness controls;
- non-sensitive technical validation that does not represent business approval
  of the provisional pilot.

## Accepted risks

The sponsor accepts that beginning Phase 1 before Phase 0 evidence is complete
may cause platform design, cost, capacity, security controls, context boundaries,
or pilot scope to be revised. Work should therefore favor small increments,
reversible decisions, isolated environments, and explicit cost limits.

## Evidence still outstanding

This authorization does not manufacture or replace:

- production plugin/customization inventory and deployed-revision verification;
- representative route, service, ECA, scheduled-job, and integration traces;
- production data volume, quality, growth, and tenant-topology profiles;
- domain workshops and approved bounded-context decisions;
- formal data classification, compliance, retention, residency, audit, privacy,
  and deletion decisions;
- approved identity, authorization, and tenant-isolation model;
- security threat-model review;
- SLO, capacity, RTO/RPO, cost, and support baselines;
- approved pilot invariants, acceptance criteria, and rollback authority;
- named product, domain, platform, data, security, operations, and migration
  owners;
- approved target-platform ADRs.

These items remain open in the Phase 0 checklist and scorecard.

## Constraints

Until the outstanding evidence and accountable approvals are recorded:

1. No production traffic is routed to a modern implementation.
2. No production data is copied, transformed, or written by migration tooling.
3. The provisional catalog pilot is not represented as finally approved.
4. Development and staging use synthetic or formally authorized non-production
   data only.
5. Infrastructure choices remain revisable hypotheses and must not create an
   irreversible production commitment.
6. Phase 0 must not be reported as complete or as having passed its exit gate.

## Stop and review conditions

Phase 1 work pauses for sponsor review if it requires production access, creates
an irreversible commitment, materially expands cost or scope, exposes regulated
data, or reveals that a Phase 0 working assumption is false. Any production
pilot requires a separate evidence-backed authorization.

## Sign-off metadata

| Field | Value |
| --- | --- |
| Decision | Proceed with Phase 1 under conditional Phase 0 gate waiver |
| Decision date | 2026-09-23 |
| Approver | Migration sponsor; identity not recorded |
| Evidence basis | Repository static discovery and explicit sponsor instruction |
| Phase 0 exit gate | Not passed |
| Production authorization | Not granted |
