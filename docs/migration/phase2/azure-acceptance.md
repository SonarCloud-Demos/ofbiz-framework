# Deferred Azure acceptance procedure

Status: **DEFERRED — no Azure infrastructure is available as of 2026-09-24.**

The Phase 2 Azure gate passes only when dated evidence demonstrates all items below in both dev and staging where applicable:

- bootstrap and environment creation from a clean checkout using federated workload identity;
- no manually created portal-only resources and no committed credentials;
- Front Door/WAF is the only public entry point and direct origin access is rejected;
- staging Key Vault, Storage, PostgreSQL, Service Bus, ACR, APIM, and workload paths meet the private-access design;
- shell and sample-service traces reach Application Insights and each configured alert is deliberately exercised;
- immutable signed digests deploy independently from infrastructure, canary successfully, and automatically roll back after a forced health failure;
- resource-group budgets and ephemeral TTL cleanup emit expected evidence;
- state, PostgreSQL, and Key Vault recovery exercises meet the approved RPO/RTO;
- full environment recreation succeeds from Terraform and the compatible-version manifest;
- security and reliability reviewers approve production creation.

Attach evidence beneath `docs/migration/phase2/evidence/<date>/`. Do not edit this status to passed based only on a Terraform plan or mocked test.
