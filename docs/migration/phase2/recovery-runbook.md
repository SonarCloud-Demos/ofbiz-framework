# Phase 2 recovery runbook

Run every exercise first in dev and record operator, UTC timestamps, resource identifiers, recovery duration, validation output, and follow-up actions. Never use production as the first exercise.

## Terraform state

1. Disable applies for the affected environment.
2. Inspect blob versions and deletion state using Azure AD authentication; never enable shared keys.
3. Copy the current state blob and its metadata to the approved evidence location.
4. Restore the last known-good blob version, acquire a normal Terraform lease with `terraform plan -lock=true`, and verify that the plan contains no unexplained replacement.
5. Re-enable applies only after two reviewers approve the recovered plan.

## PostgreSQL

1. Record the source server, backup timestamp, expected RPO, and restore target name.
2. Restore to a new server at the selected point in time. Do not overwrite the source.
3. Attach private networking and least-privilege identity through Terraform.
4. Run schema migration checks, record counts and invariants, then execute the sample-service smoke test.
5. Measure RPO/RTO, delete the exercise target through reviewed Terraform, and retain the evidence.

## Key Vault

1. Confirm purge protection and soft delete before the exercise.
2. Soft-delete a disposable test secret, recover it, and verify its version and RBAC access from the workload identity.
3. Exercise vault recovery only in an isolated recovery environment.
4. Confirm audit logs contain the delete and recovery operations without secret values.

## Full recreation

1. Preserve remote state evidence, application/image digests, and the compatible-version manifest.
2. Create a clean environment from the reviewed Terraform plan.
3. Promote the recorded shell and sample-service digests through the normal pipeline.
4. Run private-access, identity, trace, alert, budget, messaging, storage, database, canary, and rollback acceptance tests.
5. Destroy the exercise environment and confirm no orphaned billable resources remain.
