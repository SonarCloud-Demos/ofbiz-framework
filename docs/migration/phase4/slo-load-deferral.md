# Development-only SLO and load-test deferral

Status: approved project deferral on 2026-09-25 for continued repository implementation only.

## Decision

Numeric browse/search SLOs, load thresholds, production staleness, RPO/RTO and production-like reconciliation timing are deferred because no production observations or representative environment are available. The team will not invent targets from the small committed demo dataset.

This deferral permits local implementation, deterministic CI, browser acceptance, fallback drills and runbook work to continue. It does not authorize production traffic, a `modern` route marker, retirement of the legacy route or closure of Phase 4.

## Accepted development risks

- Query plans and pagination proven on demo data may not scale to the real catalog.
- The five-minute polling default may violate an eventual production staleness objective.
- Search relevance and zero-result rates may differ materially from customer traffic.
- Backfill, reconciliation, rebuild and recovery duration remain unknown at production volume.
- Capacity, alert thresholds, fallback timing and support staffing cannot yet be sized.

## Mandatory reopen conditions

Before any production cohort is enabled, assigned product, engineering and SRE owners must use an approved representative dataset and environment to:

1. record volume, query mix, latency and relevance baselines;
2. approve numeric availability, p50/p95/p99 latency, staleness and reconciliation targets;
3. approve RPO/RTO plus backup, rebuild and fallback timing;
4. execute load, soak, dependency-failure and production-like reconciliation tests;
5. record dated results and approval in Phase 4 evidence.

Failure to meet those conditions blocks cutover; it is not grounds for silently weakening the targets.
