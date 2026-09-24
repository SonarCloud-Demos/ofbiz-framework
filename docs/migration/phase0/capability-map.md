# Initial capability map

Status: approved initial baseline on 2026-09-24. Production evidence will continue to refine it during delivery.

The generated inventory is the source for raw counts and source locations. Counts indicate surface area, not business complexity. `datamodel` owns most physical entity definitions, so zero entities in a domain component does not mean that the domain owns no data today.

| Current capability | Current components | Candidate target context(s) | Current coupling/invariants to investigate | Provisional data class | Static evidence | Review state |
| --- | --- | --- | --- | --- | --- | --- |
| Identity and application access | `security`, `securityext`, `common`, `party` | Identity Access, Party | UserLogin-to-Party mapping, permission evaluation, session lifecycle, password/SSO behavior | Restricted identity/security | permissions, security entities, login tests | Security/identity review required |
| Party master and contacts | `party`, `datamodel` | Party | shared Party IDs, roles, relationships, contact mechanisms used by most domains | Personal/confidential | 266 services, 274 requests plus shared entities | Privacy/business review required |
| Product catalog | `product`, `datamodel`, `content` | Product Catalog | categories/features/content, price and inventory joins, store/catalog eligibility | Internal; possibly public subset | product contributes 835 services and 763 requests | Boundary workshop required |
| Pricing and promotions | `product`, `order`, `party` | Pricing and Promotions | customer segments, currencies, stores, promotions, accepted-price snapshot | Commercially confidential | product pricing/promo service resources and tests | Invariant workshop required |
| Inventory and facilities | `product`, `order`, `manufacturing`, `accounting` | Inventory and Facility | ATP/QOH/reservations, shipment, manufacturing issue/receipt, valuation postings | Commercially confidential | facility UI/tests, manufacturing issuance tests | Transaction workshop required |
| Order management | `order`, `product`, `party`, `accounting` | Order Management | order capture/status, price snapshot, inventory reservation, payment and returns | Personal and commercial | 465 services, 378 requests, order/cart tests | Critical-journey review required |
| Fulfilment and shipment | `product`, `order`, `manufacturing` | Fulfilment | allocation, pick/pack/ship, carrier state, inventory issue, order status | Personal and commercial | shipment services/gateways; report/integration candidates | Integration review required |
| Invoicing and billing | `accounting`, `order`, `party` | Invoicing | invoice issue/adjust/void, tax, order/party snapshots, payment allocation | Financial/confidential | invoice test suites; accounting has 704 services | Finance/audit review required |
| Payments | `accounting`, `order` | Payments | authorization/capture/refund, tokens, gateways, allocations and settlement | Restricted payment/financial | payment tests and gateway candidates | PCI/payment review required |
| General ledger | `accounting`, inventory/manufacturing sources | General Ledger | balanced postings, periods, valuation, immutable audit trail | Restricted financial | accounting services/reports | Finance/audit review required |
| Work and time management | `workeffort`, `party` | Work Management | assignment, calendar, time approval and cost facts | Personal/confidential | 167 services, 111 requests, work-effort tests | HR/business review required |
| Manufacturing | `manufacturing`, `product`, `workeffort`, `accounting` | Manufacturing | BOM/routing/MRP, inventory consumption/receipt, labor and costing | Commercially confidential | 168 services, 155 requests, MRP/issuance tests | Operations workshop required |
| Human resources | `humanres`, `party` | Human Resources | employment/position and Party identity, access separation, retention | Restricted HR/personal | 131 services, 232 requests | HR/privacy review required |
| Marketing and sales force automation | `marketing`, `party`, `product`, `content` | Marketing and CRM | consent/contact use, campaigns, opportunities and communications | Personal/confidential | 110 services, 146 requests | Consent/privacy review required |
| Content and documents | `content`, shared users | Content | metadata, binary content, versions, surveys, access policy | Mixed; owner-classified | 288 services, 347 requests | Content classification required |
| Notifications and communication delivery | `common`, `content`, domain callers | Notifications | intent versus delivery, recipient/consent, templates, retries/provider status | Personal/confidential | email and communication service definitions/tests | Recommended pilot review |
| Administration and technical operations | `webtools`, framework | Platform operations; capability-owned admin routes | generic entity/service tools, data import/export and test execution | Restricted administrative | 143 webtools requests | Replace with least-privilege operations |

## Required enrichment

For each row, the accountable product and engineering owners must add: users, business outcomes, upstream/downstream systems, production volume and seasonality, change/incident history, critical journeys, explicit invariants, current transactions, accepted downtime, data retention/residency, SLO/RPO/RTO, and disposition of historical data. Runtime traces must confirm or correct the static dependency candidates before a slice charter is accepted.
