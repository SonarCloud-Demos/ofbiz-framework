# OFBiz intended microservices architecture

## 1. Purpose and status

This document defines the intended target architecture for the OFBiz modernization in terms of modules, their responsibilities, ownership boundaries, and permitted relationships. It complements:

- `OFBIZ_current_architecture.md`, which describes the current OFBiz modular monolith; and
- `OFBIZ_microservices-plan.md`, which describes how to reach this target incrementally with a strangler migration.

This is a target-state architecture, not evidence that these modules already exist. The current repository does not yet contain tracked implementations of the modern services, web application, or Terraform estate. Service boundaries below are the initial bounded-context model and must be validated through domain discovery. Boundaries may be adjusted before implementation, but the ownership and dependency rules in this document remain architectural constraints.

The intended system is a set of independently deployable business services and user-experience modules in one monorepo. They communicate through versioned contracts, own their data, run on managed Azure services, and can also run as a useful hybrid system on a developer machine. During migration, the modern system and legacy OFBiz coexist behind one edge. In the final state, OFBiz and all transitional modules are removed.

## 2. Architectural principles

1. A module represents a business capability or a cohesive platform concern, not an OFBiz directory, database table, or technical layer.
2. Every business datum has one authoritative owning service and one writer at a time.
3. Services do not share schemas, database users, domain libraries, or ORM/entity models.
4. A service exposes behavior through a versioned API or event contract; no caller reaches into its implementation or database.
5. Synchronous calls are reserved for immediate decisions. State propagation and cross-domain reactions use events.
6. Cross-service workflows use explicit sagas with compensation, timeouts, and observable state; there are no distributed database transactions.
7. Browser code calls same-origin BFF endpoints, never internal services or databases directly.
8. Authentication is centralized through Microsoft Entra ID, but business authorization is enforced by every receiving backend.
9. Platform modules provide reusable mechanisms, not shared business logic or a new distributed monolith.
10. All Azure resources and relationships are expressed in Terraform; all deployables remain buildable and testable from the monorepo.
11. The root Gradle wrapper is the single aggregate build orchestrator: `./gradlew build` builds and verifies all old and new code, regardless of implementation language.

## 3. System context

```text
Employees / business users        Partners / external systems
              |                              |
              +---------- HTTPS -------------+
                             |
                 Azure Front Door Premium
                   WAF, TLS, route strangler
                             |
             +---------------+----------------+
             |                                |
      Modern experience                 External API surface
      Web shell + BFFs                  Azure API Management
             |                                |
             +---------------+----------------+
                             |
                  Business services plane
           synchronous APIs + asynchronous events
                             |
       +---------------------+---------------------+
       |                     |                     |
 service-owned         Azure Service Bus      external providers
 PostgreSQL/Blob       topics and queues       through adapters

Transitional branch only:
Front Door -> legacy OFBiz origin <-> OFBiz anti-corruption adapter
```

Microsoft Entra ID is outside the product boundary but is the authoritative identity provider. Azure managed services supply compute, networking, messaging, persistence, secrets, image storage, and telemetry. External payment, shipping, tax, email, and other providers are accessed only through the domain module that owns the integration.

## 4. Module taxonomy

The target has five module families:

| Family | Purpose | Deployment characteristic |
| --- | --- | --- |
| Experience modules | Browser shell, route bundles, design system, and BFFs | Web assets and independently scalable BFF/container deployments |
| Business services | Bounded contexts that own behavior and data | Independent Azure Container Apps and, where needed, Container Apps Jobs |
| Integration modules | Governed API edge, event contracts, provider adapters, and temporary OFBiz bridge | Managed gateway or independently deployed adapters |
| Platform modules | Identity integration, messaging, observability, secrets, storage, and delivery mechanisms | Azure managed services plus thin shared libraries/configuration |
| Infrastructure modules | Terraform modules and environment compositions | Applied independently from application builds with controlled state boundaries |

Only business services own business state. Experience, integration, and platform modules may own operational state—sessions, deduplication records, workflow state, configuration, or delivery status—but may not become an alternate system of record for another context.

## 5. Experience modules

### 5.1 Web shell

The web shell is the single modern browser entry point. It owns:

- application frame, navigation, global search entry, error boundaries, and route loading;
- Entra sign-in/sign-out user experience and server-backed session state;
- shared accessibility behavior, localization, responsive layout, and telemetry;
- the versioned route manifest;
- display of the persistent **Modern experience** marker on every modern route.

The shell does not contain domain workflows and does not call business services directly. It loads route modules and calls same-origin BFF endpoints. The initial composition model is build-time route packages producing one coherent web artifact. Runtime-loaded micro-frontends are permitted only after independent release needs are demonstrated and integrity, CSP, dependency isolation, and shell compatibility are designed.

### 5.2 Route modules

Route modules implement complete user journeys within a bounded context. Intended route groups correspond to the business services, such as party, catalog, inventory, order, fulfilment, invoicing, payments, ledger, work management, manufacturing, HR, marketing, and content.

Each route module:

- is owned with its domain capability or by a named UI team;
- uses the shared design system and authentication/session interfaces;
- declares routes, roles, owning BFF, feature flag, support owner, and `experience: modern` in the route manifest;
- has accessibility, authorization, component, and end-to-end tests;
- displays the marker through the shell rather than implementing its own badge;
- has no import dependency on another route's internal code.

Cross-domain navigation uses URLs and shell navigation contracts. Shared presentation behavior belongs in a deliberately versioned web package, not in another route module.

### 5.3 Design-system and web platform packages

The design system contains accessible visual primitives, tokens, layouts, and documented interaction patterns. Web platform packages provide narrow interfaces for session state, authorization hints, telemetry, localization, route registration, and API error handling.

These packages are build-time dependencies. They do not contain business entities, API orchestration, permissions decisions, or a generic client that bypasses BFFs. Breaking changes follow normal package versioning and compatibility checks even though all code is in one repository.

### 5.4 Backend-for-frontend modules

A BFF exposes use-case-shaped endpoints for one route group or a closely related set of journeys. It owns browser session handling, CSRF defenses, response composition, UI-specific caching, and translation between browser models and domain APIs.

A BFF may call several business services when a screen needs composition, but it must:

- use published APIs and constrained user/workload identity;
- enforce coarse route/use-case authorization before dispatch;
- avoid business transactions and authoritative business rules;
- set explicit timeouts and tolerate optional partial data where the journey allows;
- avoid deep fan-out and use read projections when repeated composition becomes costly;
- never access a service database, Service Bus entity, or the OFBiz adapter directly from browser code.

Initially, several route groups may share one shell BFF deployment if team ownership and scaling are aligned. Logical BFF modules remain separate so they can be deployed independently later without redesigning contracts.

## 6. Business service modules

### 6.1 Identity access service

The identity access service maps the immutable Entra tenant/subject identity to application principals and business party references. It owns application role assignments, entitlement projections, provisioning audit, and identity-to-party links; it does not own people or organizations themselves.

Relationships:

- validates identity context originating from Entra and the BFF/API edge;
- resolves the application principal and role/permission claims used by services;
- references Party IDs but obtains party facts through the Party API/events;
- publishes role/entitlement and identity-link lifecycle events;
- contains the temporary legacy session exchange submodule while OFBiz remains.

Every service still authorizes operations locally. The identity access service is not called synchronously for every request if signed tokens and locally maintained entitlement projections are sufficient.

### 6.2 Party service

The Party service owns people, organizations, roles, relationships, postal/email/telecom contact points, and their validation/lifecycle rules. It supplies stable Party IDs referenced by other contexts.

Relationships:

- publishes `PartyCreated`, `PartyUpdated`, `PartyRoleChanged`, and contact-change events;
- serves immediate party/contact queries needed for an authorized transaction;
- provides projections to Order, Invoice, Marketing, HR, and Identity Access;
- does not embed orders, employment, invoices, campaigns, or identity credentials in its model.

Other services store the Party ID and transaction snapshots they require. They do not hold foreign keys into the Party database.

### 6.3 Product catalog service

The Product Catalog service owns products, variants, categories, features, catalog membership, descriptive attributes, and sellable/purchasable lifecycle. It does not own stock quantities, calculated prices, or order lines.

Relationships:

- publishes product/category/feature lifecycle events;
- supplies product facts to Pricing, Inventory, Order, Manufacturing, Marketing, and Content projections;
- references content assets by stable Content IDs or URLs;
- receives no synchronous callbacks from consumers during catalog updates.

### 6.4 Pricing and promotions service

The Pricing service owns price lists, pricing rules, promotion eligibility, and price calculations. Its API accepts a pricing context and returns an explainable, time-bound quote.

Relationships:

- consumes product facts and relevant party/customer segment events into local projections;
- serves synchronous price/discount decisions to Order and quote-oriented BFFs;
- emits rule/version changes and optionally price-change events for downstream projections;
- Order persists the accepted price, adjustments, rule/version references, and currency as a transaction snapshot.

Pricing never updates an order or invoice database directly.

### 6.5 Inventory and facility service

The Inventory service owns facilities, inventory items, stock balances, availability, reservations, releases, adjustments, and movements. It is the sole authority for whether stock can be reserved.

Relationships:

- consumes product identifiers and unit facts from Product Catalog events;
- exposes idempotent reserve, release, commit, receive, and adjust commands/APIs;
- publishes availability, reservation, and movement events;
- participates in the order saga through commands and events;
- supplies Manufacturing and Fulfilment with owned inventory operations, not table access.

Availability projections may be eventually consistent; reservation decisions execute against authoritative Inventory state.

### 6.6 Order management service

The Order service owns carts where retained server-side, quotes, sales and purchase orders, order lines, adjustments accepted at order time, status transitions, cancellations, and returns initiation. It is the order-lifecycle system of record.

Relationships:

- consumes Party, Product, and selected policy facts into local projections;
- requests synchronous price decisions and inventory reservations only where the user response requires them;
- orchestrates or initiates an order saga for inventory, payment, fulfilment, and invoice outcomes;
- publishes order lifecycle events used by Fulfilment, Invoicing, Marketing, and analytics;
- stores immutable snapshots of names, addresses, product description, price, tax result, and terms required to preserve the transaction.

Order does not query Party or Catalog during historical order display when its own snapshot is the legally/business-relevant fact.

### 6.7 Fulfilment service

The Fulfilment service owns fulfilment plans, pick/pack tasks, shipments, packages, tracking status, and carrier interaction state.

Relationships:

- consumes accepted/ready order events;
- invokes Inventory operations for allocation/issue through published contracts;
- calls external carrier adapters that it owns;
- publishes fulfilment and shipment lifecycle events to Order, Invoicing, Notifications, and customer-facing projections;
- never changes Order status tables directly; Order reacts to fulfilment events according to its own state machine.

### 6.8 Invoicing service

The Invoicing service owns invoice documents, invoice lines, adjustments, invoice status, due dates, credit/debit notes, and invoicing audit history. Tax calculation may be an internal module or a separate service only if compliance and scaling justify it.

Relationships:

- consumes billable Order/Fulfilment events and necessary Party snapshots;
- exposes controlled invoice issue, adjustment, credit, and query operations;
- publishes invoice issued/adjusted/voided/paid-status events;
- sends accounting facts to General Ledger through events;
- receives payment allocation/settlement events from Payments rather than reading payment tables.

### 6.9 Payments service

The Payments service owns payment intents, authorization/capture/refund lifecycle, token references, provider interactions, allocations, settlement status, and reconciliation. Sensitive card data remains with a compliant payment provider; the service stores only permitted tokens and metadata.

Relationships:

- accepts idempotent payment commands tied to Order or Invoice IDs;
- owns payment-provider adapters and webhook verification;
- publishes authorized, captured, failed, refunded, allocated, and settled events;
- sends financial facts to Invoicing and General Ledger through events;
- uses Party references and billing snapshots but does not own party or invoice data.

Payments has stronger network, secret, audit, and compliance controls than general services.

### 6.10 General ledger service

The General Ledger service owns chart of accounts, accounting periods, journals, postings, balances, and financial close rules. It is append-oriented and fully auditable.

Relationships:

- consumes accounting facts from Invoicing, Payments, Inventory, Manufacturing, and other approved producers;
- validates and translates those facts into balanced postings using versioned accounting rules;
- publishes posting/period-close outcomes;
- serves accounting queries and creates curated feeds for financial reporting;
- does not synchronously participate in customer-facing order/payment success unless a business invariant explicitly requires it.

### 6.11 Work management service

The Work Management service owns projects/work efforts, tasks, calendars, assignments, time entries, and work status. It references Party IDs for assignees and may publish approved time/cost facts to Accounting and Manufacturing.

It consumes Party and, when needed, Product/Manufacturing reference events into local projections. It does not own employee master data or production-run state.

### 6.12 Manufacturing service

The Manufacturing service owns bills of material, routings, production plans, material requirements planning results, work/production orders, and production-run state.

Relationships:

- consumes Product Catalog definitions and facility/reference changes;
- uses Inventory commands for component reservation/consumption and finished-goods receipt;
- integrates with Work Management for labor/task facts where applicable;
- publishes production and costing facts to Inventory, Order, and General Ledger;
- does not share BOM or inventory tables with other services.

### 6.13 Human resources service

The HR service owns employment relationships, positions, organizational assignments, leave/HR workflows, and HR-specific records. It references Party IDs rather than duplicating the person master.

HR data is subject to stricter authorization, logging redaction, retention, and database access policies. Only minimal employment events are published, and consumers receive the least information required. Payroll is outside this boundary unless explicitly brought into scope by a later architecture decision.

### 6.14 Marketing and CRM service

The Marketing service owns campaigns, segments defined for marketing use, leads/opportunities, campaign membership, and sales-force workflow.

Relationships:

- consumes consent-safe Party/contact changes and Product facts;
- requests approved Notifications rather than sending mail directly;
- publishes opportunity and campaign outcomes;
- does not become the owner of Party contact data or Product definitions;
- enforces consent and suppression policies at the point of campaign execution.

### 6.15 Content service

The Content service owns content metadata, document relationships, versions, publication state, surveys where retained, and access policy. Binary objects live in Azure Blob Storage behind service-controlled access.

Relationships:

- supplies stable Content IDs and time-limited authorized download/upload operations;
- publishes content lifecycle events;
- allows Catalog, Marketing, Party, and other contexts to reference content without accessing storage directly;
- integrates malware scanning and content validation before publication.

### 6.16 Notifications service

The Notifications service owns notification requests, template rendering versions, delivery attempts, provider responses, suppression handling, and delivery status. It is a delivery capability, not the owner of business communication intent.

Relationships:

- consumes explicit notification commands/events from business services;
- obtains only the recipient and template data needed for delivery, preferably in the command;
- owns email/SMS/push provider adapters and retry/DLQ behavior;
- publishes delivered, failed, and suppressed outcomes;
- cannot infer that a domain transaction succeeded merely because a notification was delivered.

## 7. Business context relationship map

The following table records the intended dominant relationships. It is not an exhaustive endpoint list.

| Source | Target | Relationship | Preferred mode |
| --- | --- | --- | --- |
| Identity Access | Party | Resolve/validate linked business party | API for immediate resolution; Party events for projection |
| Product Catalog | Pricing | Product and classification facts | Events |
| Product Catalog | Inventory | Stocked-item reference facts | Events |
| Product Catalog | Order | Orderable product projection | Events; API only for exceptional fresh validation |
| Product Catalog | Manufacturing | Product/BOM reference inputs | Events |
| Party | Order/Invoicing/HR/Marketing | Party lifecycle and scoped contact facts | Events plus authorized APIs |
| Pricing | Order | Price and promotion decision | Synchronous API; accepted result snapshotted by Order |
| Order | Inventory | Reserve/release/commit stock | Idempotent command/API plus outcome events |
| Order | Payments | Authorize/capture/refund workflow | Saga commands and events |
| Order | Fulfilment | Initiate/cancel fulfilment | Events/commands |
| Order/Fulfilment | Invoicing | Billable facts | Events |
| Payments | Invoicing | Allocation and settlement facts | Events |
| Invoicing/Payments | General Ledger | Accounting facts | Events |
| Inventory/Manufacturing | General Ledger | Valuation/costing facts | Events |
| Manufacturing | Inventory | Reserve, consume, and receive materials | Commands/APIs plus events |
| Business services | Notifications | Requested business communication | Asynchronous commands/events |
| Business services | Content | Store/fetch authorized content | API; lifecycle events |
| BFFs | Business services | UI use cases and composed reads | Synchronous APIs only |
| Any modern service | OFBiz | Transitional legacy behavior | Only through OFBiz adapter |

The direction shown for events is producer to consumer. The consumer owns its projection and decides how to react. A producer must not depend on the number, availability, or internal behavior of event consumers.

## 8. Internal structure of a business service

Every business service follows a consistent internal dependency direction without requiring identical frameworks:

```text
API/event adapters  --->  application/use-case layer  --->  domain model
       |                         |                           |
       +---------------- infrastructure adapters <----------+
                    PostgreSQL, Service Bus, Blob, providers
```

Suggested source modules are:

| Internal module | Responsibility | May depend on |
| --- | --- | --- |
| `domain` | Aggregates, value objects, invariants, domain events, policies | Standard language/runtime libraries only |
| `application` | Commands, queries, use-case orchestration, transaction boundaries, ports | `domain` |
| `api` | HTTP endpoints, request validation, auth context mapping, OpenAPI implementation | `application`, API DTO/contracts |
| `messaging` | Event/command consumers and published-schema mapping | `application`, event contracts |
| `persistence` | PostgreSQL repositories, migrations, outbox/inbox | application ports, infrastructure libraries |
| `integrations` | External provider clients and resilience policy | application ports, generated external clients |
| `bootstrap` | Dependency wiring, configuration, health/readiness, telemetry | all service-local modules |

API/event DTOs are not domain entities. Generated clients and contract packages contain wire shapes only. Services may share small technical libraries for telemetry, HTTP conventions, security middleware, outbox mechanics, and test fixtures. They may not share domain models, repositories, database migration code, or a universal “common” package containing business behavior.

## 9. Integration modules

### 9.1 API Management

Azure API Management is the external and partner API gateway. It validates tokens, applies quotas/rate limits, performs coarse routing and safe protocol policy, and publishes developer-facing API products. It does not implement domain workflows, store business state, or replace BFF/service authorization.

Internal service-to-service calls normally use private Container Apps ingress and Entra/managed-identity tokens without traversing APIM unless a specific governance requirement justifies it.

### 9.2 Event contract catalog

The contract catalog under `contracts/events/` contains schemas, semantic documentation, compatibility fixtures, producer/consumer ownership, classification, and deprecation dates. Azure Service Bus transports events but is not the schema registry or source of contract truth.

Integration events include event ID, type/schema version, occurrence time, producer, subject/aggregate ID, correlation ID, causation ID, tenant/business scope where applicable, and a payload without secrets. Delivery is at least once; ordering is guaranteed only within an explicitly configured session/partition scope. Consumers must handle duplicates and valid out-of-order arrival.

### 9.3 External provider adapters

The business service that owns a provider relationship also owns its adapter—for example Payments owns payment providers and Fulfilment owns carriers. A separate adapter deployment is justified only for isolation, protocol, security, or scaling needs. Even then, its API is private to the owning context; it is not a shared provider database or generic enterprise service bus.

### 9.4 OFBiz anti-corruption adapter (transitional)

The OFBiz adapter is the sole modern module allowed to understand OFBiz named services, legacy entity shapes, controller behavior, and legacy error semantics. It exposes narrow modern contracts and translates identity, requests, results, and change feeds.

Allowed relationships:

- modern services call it only for documented behavior not yet migrated;
- it calls named OFBiz services through a purpose-built integration boundary;
- it may consume controlled legacy change feeds/CDC and emit normalized migration events;
- data migration jobs may use its bulk interfaces.

Forbidden relationships:

- generic entity CRUD;
- permanent service access to the OFBiz database;
- browser or route-module calls directly to the adapter;
- new business behavior implemented in the adapter;
- use after the last OFBiz capability is retired.

The legacy identity bridge/session exchange is a companion transitional module. Neither adapter nor bridge exists in the final architecture.

## 10. Data and state modules

### 10.1 Service databases

Each business service owns a PostgreSQL database and database principal. Multiple databases may share an Azure PostgreSQL Flexible Server initially, but this is infrastructure consolidation, not logical sharing. A service cannot receive credentials for another service's database.

Within its database a service owns:

- domain tables and constraints;
- schema migration history;
- outbox records written in domain transactions;
- inbox/idempotency records for received messages/commands;
- local read models and saga state belonging to its context;
- operational retention and archival procedures.

Cross-service foreign keys, views, triggers, joins, and shared migration scripts are prohibited. Cross-domain reporting is fed by events or governed extracts into an analytical store.

### 10.2 Blob/object storage

Content owns business documents in Blob Storage. Other services may own context-specific blobs such as exports, but every container has an explicit owner, retention policy, classification, encryption policy, and managed-identity access. Clients use short-lived, least-privilege access negotiated by the owning service rather than broad storage keys.

### 10.3 Cache and session state

Azure Managed Redis is optional and introduced only for a measured need. BFF session state and service caches are namespaced and owned; cached data is disposable. Redis is never used to bypass another service or as the authoritative record.

### 10.4 Analytical/read-model data

Cross-domain dashboards and reporting use curated event-fed projections or an analytical platform. The analytical consumer is downstream: it cannot write operational service databases or become a hidden synchronous dependency of transactions. Financial reports requiring ledger authority use General Ledger APIs/projections with reconciled provenance.

## 11. Messaging and workflow relationships

Azure Service Bus provides:

- topics/subscriptions for published integration events;
- queues for asynchronous commands with one logical owner;
- dead-letter queues and operational replay;
- duplicate detection and sessions where a workflow explicitly requires ordered handling.

Every state-changing service uses a transactional outbox. Relays publish only committed records and mark progress safely. Consumers use an inbox/idempotency mechanism and commit state before acknowledging. Retries use bounded exponential backoff; poison messages reach a DLQ with alerting and a reviewed replay path.

Long-running workflows have an owning saga module. For example, Order Management normally owns the order-placement saga because it owns the business goal. It records current state and deadlines, sends commands to Inventory/Payments/Fulfilment, consumes outcomes, and issues compensating commands such as release reservation or refund. Participant services retain authority over their own state and may reject commands based on local invariants.

Services do not treat event delivery as an exactly-once guarantee and do not use Service Bus as long-term event storage. Event replay beyond retention comes from governed archives or re-publication procedures owned by the producer.

## 12. Security relationships

### 12.1 Human request chain

```text
Browser -> Front Door/WAF -> web shell/BFF -> business service
             TLS             secure session     token + authorization
               \-> Entra ID authorization-code flow with PKCE
```

The browser receives secure, HTTP-only, same-site session cookies from the BFF and does not persist bearer tokens in local storage. The BFF validates identity, applies CSRF protection, and uses on-behalf-of or an approved constrained token pattern where user delegation is required. Business services validate token issuer, audience, signature, time, tenant, and required permission, then apply resource-level domain authorization.

### 12.2 Workload chain

Container Apps and Jobs have distinct managed identities. They obtain tokens for explicitly allowed service audiences and Azure resources. Azure RBAC grants least privilege to Key Vault, Service Bus, Storage, ACR, and monitoring. Database authentication uses Entra/managed identity where supported by the selected implementation; otherwise credentials are held in Key Vault and rotated.

No shared all-services identity, database user, Service Bus policy, or Key Vault access policy is permitted. High-sensitivity services such as Payments and HR receive separate identities, narrower networks, stronger audit, and restricted operator access.

### 12.3 Network chain

Front Door Premium is the public ingress. WAF, TLS policy, host validation, rate limiting, and origin protection are centralized there. APIM is public only for approved API products. Modern service ingress is internal; PostgreSQL, Service Bus, Key Vault, Storage, Redis, and ACR use private connectivity/private DNS in production where supported.

Outbound access uses controlled NAT/egress and explicit provider allowlists where practical. The OFBiz origin is not directly public during migration. Network isolation supplements but never replaces identity and application authorization.

### 12.4 Modern-page marker trust boundary

The visible Modern experience marker is a UI provenance signal, not an authorization control. The shell renders it only from the reviewed build-time route manifest with `experience: modern`. Query parameters, response headers from arbitrary services, or user content cannot enable it.

CI requires every modern route entry to identify its owner, roles, BFF, API contracts, feature flag, fallback, and end-to-end test. The marker contains accessible text and `data-experience="modern"`. A support detail may show non-sensitive UI/service versions and correlation ID. Legacy pages do not receive the marker; an optional legacy marker must be visually and semantically distinct.

## 13. Platform modules

### 13.1 Azure Container Apps compute plane

Every service/BFF is an independently revisioned Container App with its own image, identity, configuration, scaling rules, health/readiness probes, resources, and ingress policy. Event-driven or scheduled processing uses Container Apps Jobs when continuous service hosting is unnecessary.

Services scale independently. Scale-to-zero is allowed only when its cold-start and availability effects meet the SLO. Revisions support canary traffic and rollback, but data and message compatibility must make rollback safe.

### 13.2 Edge and routing plane

Azure Front Door Premium owns public DNS routing, TLS, WAF, and the strangler route split. The version-controlled route manifest is the application source for route intent; Terraform/environment configuration renders and validates concrete routing rules.

In the final state, all application paths resolve to modern origins. During migration, unmatched or explicitly legacy paths resolve to OFBiz. Route fallback is not considered safe after data-writer transfer unless the migration ledger defines compatible reverse projection or forward repair.

### 13.3 Secrets and configuration plane

Key Vault contains secrets, certificates, and keys that cannot be replaced with managed identity. Services refer to them through managed identity and configuration references. Non-secret configuration is environment-specific, validated at startup, and recorded with the deployment. Business configuration with lifecycle/audit requirements belongs to the owning service, not environment variables.

### 13.4 Observability plane

OpenTelemetry instrumentation sends traces, metrics, and structured logs to Application Insights/Azure Monitor and Log Analytics. Front Door request IDs, W3C trace context, business-safe correlation IDs, message correlation/causation IDs, deployment revision, and service version are propagated end to end.

Each deployable owns SLOs, dashboards, alerts, and runbooks. Platform dashboards show edge health, Container Apps revisions/scaling, PostgreSQL saturation, Service Bus lag/DLQs, dependency failures, and cost. Logs redact tokens, secrets, payment data, and classified personal/HR data.

### 13.5 Container registry and software supply chain

ACR holds immutable, content-addressed images. CI builds once, attaches SBOM/provenance, scans and signs/attests the image, then promotes that same artifact through environments. Deployment policy rejects unapproved images. Repository source version, contract version, database migration, and deployed image digest remain traceable.

## 14. Azure deployment topology

```text
Azure tenant
|
+-- Production subscription
|   +-- edge resource group: Front Door, WAF, DNS bindings
|   +-- platform resource group: VNet, Container Apps environment, ACR links
|   +-- data resource group: PostgreSQL, Service Bus, Storage, Redis
|   +-- operations resource group: Monitor, App Insights, Log Analytics, alerts
|   +-- service resources: Container Apps/Jobs, identities, service databases
|
+-- Non-production subscription(s)
    +-- dev environment
    +-- staging environment
    +-- ephemeral integration environments with TTL
```

Production and non-production do not share identities, databases, Service Bus namespaces, vaults, or Terraform state. Environments use isolated resource groups and preferably separate subscriptions according to governance requirements. Availability-zone redundancy is enabled for production services where supported and justified. Region count, failover topology, data residency, and exact Service Bus tier are selected from explicit SLO/RPO/RTO requirements rather than assumed.

Azure API Management may be shared at an environment level while APIs remain separately versioned and owned. PostgreSQL physical servers may host multiple service databases initially; identities and databases remain isolated. Higher-risk or high-scale contexts can move to dedicated servers without changing logical contracts.

## 15. Terraform module architecture

Terraform is organized into reusable modules and thin environment compositions:

```text
infra/
  bootstrap/
    state/                    # remote state account/container and CI identity
  modules/
    network/
    private-dns/
    front-door/
    api-management/
    container-app-environment/
    container-app/
    container-app-job/
    managed-identity/
    postgres-server/
    postgres-database/
    service-bus/
    key-vault/
    storage/
    observability/
    acr/
    budget-alerts/
  environments/
    dev/
    staging/
    prod/
```

Module relationships follow these rules:

- `network` and `private-dns` provide connectivity primitives to platform/data modules;
- `container-app-environment` depends on network and observability outputs;
- each `container-app` composition binds one image, identity, configuration, ingress, scaling, telemetry, and required private resources;
- data modules expose endpoints/resource IDs, never credentials through ordinary outputs;
- edge composition consumes only approved origin endpoints and the rendered route configuration;
- service compositions grant identity-specific RBAC and database access;
- environment roots pin module/provider versions and contain no application business configuration.

State is split by lifecycle/blast radius: foundation, edge, data/messaging, platform, and independently deployable services where valuable. Remote state uses Azure Storage with RBAC, blob locking, versioning, and recovery. Cross-state dependencies are few and stable. CI uses federated workload identity, saved plans, policy/security checks, protected applies, and drift detection.

## 16. Monorepo module architecture

```text
services/
  identity-access-service/
  party-service/
  product-catalog-service/
  pricing-service/
  inventory-service/
  order-service/
  fulfilment-service/
  invoicing-service/
  payments-service/
  general-ledger-service/
  work-management-service/
  manufacturing-service/
  human-resources-service/
  marketing-service/
  content-service/
  notifications-service/
  ofbiz-adapter/              # transitional
web/
  shell/
  bff/
  routes/<bounded-context>/
  packages/design-system/
  packages/web-platform/
contracts/
  openapi/<owner>/
  events/<owner>/
  compatibility/
infra/
  bootstrap/
  modules/
  environments/
local-dev/
  compose.yaml
  config/
  fixtures/
applications/, framework/, themes/   # transitional OFBiz source
```

The directory list expresses intended logical modules, not a requirement to scaffold every service before it is migrated. A service directory is created when its boundary, owner, and first vertical slice are accepted.

Dependency rules are enforced with build/lint architecture tests:

- a service cannot import source from another service;
- route modules may import only approved web packages and their generated BFF client;
- BFFs and services consume versioned contracts, not provider implementation modules;
- shared packages are technical and have named owners; domain code is never moved to `common` merely for reuse;
- `ofbiz-adapter` dependencies are forbidden outside explicit transitional ports;
- generated sources/build products are not committed;
- every tracked source-bearing directory is registered in the root Gradle graph or has an explicit reviewed exclusion;
- path-aware CI is supplementary and cannot replace the required whole-repository Gradle/Sonar gate.

Every deployable has ownership metadata, build/test/container tasks, API/event compatibility checks, database migration tasks where applicable, and an immutable image version. The environment manifest records the compatible image digests deployed together without turning the repository into one indivisible release.

### 16.1 Aggregate Gradle build and analysis

The existing root Gradle wrapper remains the canonical build interface. `./gradlew build` must include:

- all active legacy OFBiz Java/Groovy code, resources, tests, and existing quality checks;
- every modern service, BFF, job, adapter, and migration utility;
- the web shell, route modules, design system, and web platform packages;
- OpenAPI/event generation, linting, and compatibility verification;
- non-mutating Terraform/local-development format, lint, and validation checks.

Modern JVM modules are ordinary Gradle subprojects. Frontend/non-JVM modules are also represented as Gradle projects or lifecycle tasks. Their Gradle tasks provision/use pinned Node.js and package-manager versions, perform frozen dependency installation, compile, type-check, lint, test, and write coverage/test results to stable build directories. Terraform checks similarly use a pinned, reproducible tool; `build` never performs `terraform plan` against protected environments or applies infrastructure.

The root `build` lifecycle depends on all module build/verification lifecycles. Registration is enforced automatically: adding a recognized source root without its Gradle registration fails the architecture check. Focused module tasks remain available for developer speed, but a successful focused build is not a substitute for the aggregate result.

Whole-codebase Sonar analysis remains a required CI property. The authoritative gate executes `./gradlew build sonar` or two equivalent root Gradle invocations against the same commit after reports have been produced. Root Sonar configuration includes the legacy tree plus applicable sources/tests under `services/`, `web/`, `contracts/`, `infra/`, and `local-dev/`; it imports supported JVM and frontend coverage/test/analyzer reports. A new module is incomplete until both the root build graph and Sonar inputs include it.

`./modern build` is a convenience alias that delegates directly to `./gradlew build`; it cannot implement a separate aggregate build or module registry. Independent deployability therefore coexists with a single verifiable repository build.

## 17. Local-development architecture

The local environment preserves logical boundaries with production while substituting local implementations for Azure-managed infrastructure:

```text
Developer browser
      |
local edge/reverse proxy (one URL)
      +-- web shell/BFF
      +-- selected modern services
      +-- legacy OFBiz fallback during migration

Docker Compose dependencies:
PostgreSQL databases/users per service
local message broker behind the messaging port
Azurite for Blob-compatible behavior
local OIDC/dev identity profile
OpenTelemetry Collector and optional trace UI
fixtures and database-migration jobs
```

The root `./gradlew build` command is the stable full source-build interface. The root `./modern` command provides environment-oriented `bootstrap`, `build`, `test`, `up`, `down`, `logs`, and `smoke` workflows, with profiles for the full hybrid system or selected contexts; its `build` action delegates to `./gradlew build`. `up` waits for readiness, applies migrations, loads deterministic fixtures, and reports one URL. `smoke` tests authentication, one legacy route during migration, each completed modern route and marker, service health, messaging, and database isolation.

Local substitutes implement narrow application ports and do not change domain code. Tests against ephemeral Azure environments cover differences that emulators cannot prove, including Entra, managed identity, private networking, Service Bus delivery behavior, PostgreSQL authentication, Key Vault, scaling, and Front Door/APIM policy.

## 18. Allowed and forbidden relationship summary

### Allowed

- browser to shell/BFF over same-origin HTTPS;
- BFF to business service through an authorized, versioned synchronous API;
- service to service through a versioned API for an immediate decision;
- service to Service Bus through owned topics/subscriptions/queues and versioned contracts;
- service to its own database, outbox/inbox, cache namespace, and owned storage;
- service to an external provider through its context-owned adapter;
- temporary modern-service access to legacy behavior through the OFBiz adapter only;
- analytical projections consuming curated events without entering transaction paths.

### Forbidden

- browser to internal service, Service Bus, database, or OFBiz adapter;
- service reading or writing another service's database/schema/storage container;
- cross-service database joins, foreign keys, or shared ORM/entity classes;
- service imports from another service's source tree;
- synchronous call chains used to reconstruct distributed joins on every request;
- domain decisions in Front Door, APIM, BFF, generic platform libraries, or Terraform;
- service-to-service reliance on shared OFBiz `Delegator`, dispatcher, transaction, session, or cache;
- application dual writes as a steady-state integration mechanism;
- shared workload identities, broad Service Bus keys, or secrets in source/configuration logs;
- a route labelled modern when its backend still bypasses the owned BFF/service/data boundary.

## 19. Final-state dependency model

At completion, dependencies point inward toward owned domain behavior and outward only through declared ports:

```text
Web route -> BFF -> owned/public business APIs
                         |
                         +-> service domain -> owned PostgreSQL/Blob
                         +-> outbox -> Service Bus -> consumer projections/sagas
                         +-> context-owned external adapters

Entra -> authenticated principal -> authorization at BFF and every service
Terraform -> Azure platform resources -> independently promoted deployables
OpenTelemetry <- every edge, BFF, service, job, and message handler
```

There is no OFBiz origin, OFBiz adapter, legacy identity exchange, shared operational database, or legacy route. Front Door sends all product paths to modern origins. Each business service can be built, tested, deployed, scaled, restored, and retired on its own contract and data lifecycle. The monorepo supplies coordinated discovery and tooling, not runtime coupling.

## 20. Boundary decisions still requiring validation

The following items must be resolved through architecture decisions and domain evidence before the affected module is implemented:

- whether carts belong permanently in Order Management or in a separate high-scale commerce context;
- whether tax calculation is internal to Pricing/Invoicing or a separate compliance capability;
- the division of shipment planning between Inventory, Order, and Fulfilment;
- whether purchase orders remain in Order Management or form a procurement context;
- whether agreements/contracts belong in Party or a separate contract context;
- the split between operational Content, surveys, and communication history;
- whether Work Management and Manufacturing labor planning share a boundary;
- the scope of HR and whether payroll is explicitly out of system;
- required analytical platform and historical-data access model;
- workforce versus external Entra tenant model and tenant isolation semantics;
- final regional topology, data residency, availability targets, RPO/RTO, and Service Bus tier;
- which BFFs require independent deployment versus logical separation in a shared host.

These open decisions do not permit temporary shared databases or undocumented dependencies. Until resolved, keep the boundary coarse, contracts explicit, and ownership singular.
