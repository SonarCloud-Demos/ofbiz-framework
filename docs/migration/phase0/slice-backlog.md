# Initial slice backlog and recommendation

Status: approved on 2026-09-24. Scores remain hypotheses to validate with production evidence.

Scale: 1 is low/unfavorable and 5 is high/favorable. For “blast radius” and “data sensitivity,” a higher score means safer/lower. Total is unweighted and is used only to make assumptions visible.

| Candidate vertical slice | Business learning/value | Boundary clarity | Low blast radius | Low data sensitivity | Platform coverage | Static confidence | Total / 30 | Principal unknowns |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Outbound notification delivery plus delivery-status admin route | 3 | 4 | 4 | 3 | 5 | 3 | 22 | actual providers/callers, consent ownership, delivery volumes |
| Product category browse/search route | 4 | 3 | 4 | 5 | 3 | 2 | large product surface, pricing/store eligibility, content coupling |
| Party contact details route | 4 | 3 | 3 | 1 | 3 | 3 | privacy, identity mapping, broad downstream use |
| Inventory availability/reservation route | 5 | 3 | 1 | 4 | 5 | 3 | transactional invariants, peak concurrency, order/manufacturing coupling |

## Approved first slice

The approved first slice is **outbound notification delivery plus a modern delivery-status administration route**. It can exercise an independently owned database, asynchronous messaging, an external-provider adapter, retries/DLQ, BFF/UI security, Entra authorization, observability, Terraform, and the Modern experience marker without placing order/payment correctness in the first production cutover.

The slice boundary is delivery only. The calling business context continues to own the decision and consent to communicate; the Notifications context owns template-version rendering if approved, provider submission, attempts, suppression-at-delivery, and status. It must not infer business success from delivery.

## Acceptance gates before selection is final

- trace and enumerate every current email/communication service caller and scheduled job;
- identify providers, credentials, callbacks, retry behavior, templates, volumes and peak rate;
- assign consent/suppression ownership and classify message content/recipient data;
- prove a fallback/rollback that cannot send duplicates;
- define the modern admin route, permissions, SLO, RPO/RTO, retention and support owner;
- confirm business value and that the slice is representative enough to validate the platform;
- obtain product, engineering, security/privacy and SRE approval below.

| Approval | Owner | Decision/date |
| --- | --- | --- |
| Product value and route scope | Project approval | Approved 2026-09-24 |
| Domain boundary and data ownership | Project approval | Approved 2026-09-24 |
| Security/privacy and classification | Project approval | Approved 2026-09-24; detailed controls remain slice inputs |
| SLO/operations/on-call | Project approval | Approved 2026-09-24; measured targets remain slice inputs |
| Migration/cutover safety | Project approval | Approved 2026-09-24; cutover plan still requires evidence |

If implementation evidence invalidates this selection, retain the scoring method, update the assumptions, and approve a replacement explicitly.
