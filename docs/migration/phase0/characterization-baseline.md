# Legacy characterization baseline

Status: approved Phase 0 baseline on 2026-09-24. Runtime critical-journey and performance measurements remain required before production cutover.

## Automated now

- `./gradlew verifyPhase0Inventory` locks the registered components, services, entities, ECAs, controllers, permissions, jobs, test suites and static dependency candidates to a reviewable baseline.
- `verifyPhase0Inventory` is attached to `check`, so metadata drift must update the committed evidence.
- Existing JVM unit tests run through `./gradlew test`.
- Existing OFBiz integration suites run through `./gradlew testIntegration`; the generated inventory lists their suite names and source files.
- `./gradlew build` retains the project's compilation, unit, style, documentation and other existing verification behavior.

Static inventory checks are characterization of structure, not business journey tests.

## Existing domain-oriented test evidence

Repository test definitions cover examples in accounting/invoice/payment/rates, content, manufacturing inventory issuance/MRP/production runs, marketing, order/cart/quote/request/shopping lists, party/contact/status, product/catalog/facility/configuration/promotions/cost, work effort, authentication, entity/service behavior, REST, webapp and widget behavior. Their presence does not establish production coverage or current pass/fail baselines until `testIntegration` is executed against a documented clean dataset and result archive.

## Critical journeys requiring automated characterization

| Journey | Minimum observable assertions | Test level | Baseline status |
| --- | --- | --- | --- |
| Login, authorization denial and logout | session/token behavior, permission denial, audit/no secret leakage | browser + service | Missing |
| Party create/update/contact lifecycle | persisted state, ECA side effects, permissions | API/service + database observation | Existing partial tests; baseline pending |
| Catalog browse and price lookup | eligibility, currency/price result, category/content rendering | browser + service | Existing partial tests; baseline pending |
| Inventory availability/reserve/release | QOH/ATP, concurrency, rollback and accounting side effects | service/integration | Existing partial tests; load baseline missing |
| Order capture through status progression | snapshots, price, reservation, payment/fulfilment triggers, rollback | browser + integration | Existing partial tests; end-to-end missing |
| Invoice issue/payment allocation/posting | balances, status, journal effects, audit | integration | Existing partial tests; end-to-end baseline pending |
| Notification delivery | caller intent, template, idempotency, retry/failure, provider result | integration with provider stub | Missing consolidated journey |
| Scheduled job execution/recovery | single execution, retry, result, restart behavior | integration | Pending inventory review |

## Performance and production baseline protocol

For every critical journey, capture request rate, concurrency, payload-size bands, p50/p95/p99 latency, error/timeout rate, database time, cache behavior and downstream dependency time over normal and peak windows. Record dataset, hardware/deployment topology, JVM/database configuration and test commit. Sanitized production observations take precedence over an artificial local load test, but both are required before sizing the Azure target.

## Gate

Phase 0 approval accepts this characterization baseline. Before a production slice is cut over, its business owner must select the applicable critical journeys, the tests must run successfully on a reproducible production-like dataset, production measurements must be recorded in `requirements-baseline.md`, and security/SRE must approve its evidence collection and acceptance thresholds.
