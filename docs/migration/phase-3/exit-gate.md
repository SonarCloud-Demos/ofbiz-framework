# Phase 3 exit-gate evidence

| Requirement | State | Evidence / remaining work |
| --- | --- | --- |
| Independent service and data ownership | Implemented locally | `services/product-catalog-service`; isolated Compose PostgreSQL/Flyway schema |
| No direct modern access to OFBiz | Enforced statically | Phase 3 architecture check |
| Versioned search/detail contract | Implemented | `platform/contracts/http/product-catalog-v1.yaml` |
| Authorization and modern UI | Implemented locally | BFF `CATALOG` role; accessible search/detail shell view |
| Trusted route classification | Implemented locally | `/catalog/products` is `hybrid` in route catalog and edge |
| Backfill and change capture | Implemented; not environment-exercised | Keyed OFBiz adapter, stable cursor, atomic checkpoints and idempotent upserts |
| Reconciliation | Implemented; approval/evidence pending | Cutoff snapshot, deletion handling, run history and status endpoint; golden rules remain owner-approved evidence |
| Local seed and smoke | Implemented | `./modern seed`; service readiness and trusted Hybrid edge header checks |
| Azure workload IaC | Implemented; not deployed | Catalog database, identity, internal Container App, secrets, probes and restart alert |
| Telemetry and operations | Implemented baseline | Structured sync/audit logs, Prometheus counters, SLO/rollback/restore runbook |
| Production-condition canary and soak | Not started | Requires Phase 0 evidence and an Azure environment |
| Security/accessibility/performance validation | Not started | Test representative volume and approved identity/data scopes |
| Game day and database restore | Not started | Exercise legacy fallback, replay, dead letter handling and restore |
| Legacy route/code removal | Not started | Only after approved soak; OFBiz remains authoritative |

The Phase 3 exit gate is **open**. The repository increment must not be described
as production-ready or as a transfer of product ownership.
