# Initial bounded-context and dependency map

Status: approved initial context map on 2026-09-24. Runtime evidence may refine boundaries through subsequent ADRs.

## Intended context relationships

```text
Identity Access ---- party link ----> Party
                                      |
Product Catalog ---> Pricing --------+------> Order Management
       |                  quote                 |
       +------------> Inventory <--- reserve ---+
       |                  |                     |
       +------------> Manufacturing             +--> Fulfilment
                                               |
                         Payments <-------------+
                             |                  |
                             +--> Invoicing <---+
                                      |
Inventory / Manufacturing / Payments / Invoicing
                    +------ accounting facts ------> General Ledger

Party/Product facts --> Marketing, HR, Work Management
Business contexts -- notification requests --> Notifications
Business contexts -- content references/API --> Content
```

Arrows represent published APIs, commands, or events—not shared tables. Exact modes are defined in the intended architecture.

## Candidate invariant owners

| Invariant/decision | Intended authority | Important participants |
| --- | --- | --- |
| A principal may perform a business action | Receiving service using Identity Access claims/projections | Entra ID, Party link |
| Party/contact record is valid and active | Party | Identity, Order, Invoice, HR, Marketing |
| Product is sellable in a catalog | Product Catalog | Pricing, Order |
| Price/promotion is valid for a supplied context | Pricing | Product and Party projections; Order snapshots result |
| Stock can be reserved/released/issued | Inventory | Order, Fulfilment, Manufacturing |
| Order status transition is legal | Order Management | Inventory, Payments, Fulfilment, Invoicing outcomes |
| Shipment/fulfilment transition is legal | Fulfilment | Order, Inventory, carriers |
| Payment transition is legal and provider result authentic | Payments | Order, Invoice, provider |
| Invoice balances and lifecycle are valid | Invoicing | Order/Fulfilment facts, Payment allocations |
| Journal is balanced and accounting period permits posting | General Ledger | Accounting-fact producers |
| Production order/BOM/routing transition is valid | Manufacturing | Product, Inventory, Work Management |
| Recipient may be contacted and delivery is attempted | Calling domain owns intent/consent; Notifications owns delivery | Party/Marketing/provider |

## Current coupling to validate

The current system can span all components in one service transaction and one database. The generated dependency candidates identify component-resource references and literal service/entity calls but cannot see reflection, dynamically composed service names, database effects, all ECAs, or production frequency. Trace validation must answer:

1. which cross-component calls occur on each critical journey;
2. which entity writes share a transaction and what rollback preserves;
3. which ECAs and scheduled jobs create delayed or implicit behavior;
4. which views/reports perform cross-domain joins;
5. which integrations have delivery, ordering, idempotency or reconciliation guarantees;
6. which reads require fresh decisions versus a local event-fed projection.

No proposed context is approved merely because it resembles an existing OFBiz component. Keep boundaries coarse until these questions are answered.
