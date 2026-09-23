# Phase 1 recovery and rollback runbook

## Container Apps revision rollback

Identify the last healthy immutable image digest, confirm schema compatibility,
shift a small traffic weight to its retained revision, run synthetic checks,
then move the remaining weight. Never rebuild the old version. Record the
trigger, revision IDs, timestamps, metrics, and decision owner.

## PostgreSQL restore

Restore to a new server at the selected point in time, validate migrations and
reconciliation using read-only credentials, then update the Key Vault reference
through a reviewed deployment. Do not overwrite the damaged server. Preserve it
for investigation until retention approval.

## Service Bus dead letters

Pause the failing consumer, classify the failure, fix or quarantine poison
messages, and replay with the original message and idempotency IDs. Monitor
duplicates, ordering, queue age, and business reconciliation. Never bulk-delete
the DLQ to recover service.

## Regional recovery

Phase 1 assumes redeployment from Terraform and immutable artifacts into an
approved alternate Azure region. Database recovery method and RTO/RPO remain
unapproved Phase 0 evidence; a game day must measure them before production.

## Evidence record

Every exercise records environment, operator, start/end time, objective,
artifact digests, recovery point, measured RTO/RPO, reconciliation result,
alerts observed, gaps, and accountable follow-up owner.
