# OFBiz microservices migration plan

## 1. Purpose and guiding decision

This plan migrates the current OFBiz modular monolith to a modern, microservices-based architecture on Microsoft Azure while keeping the system available throughout the migration. It uses a **strangler fig pattern**: a common edge routes each capability to either OFBiz or a modern implementation, and ownership moves in small, reversible slices until OFBiz can be retired.

The repository remains a monorepo. Every deployable unit is independently buildable and deployable, while a single supported local command builds and starts the complete hybrid system for development and acceptance testing.

This is an incremental replacement, not a distributed rewrite of OFBiz classes. A slice is considered migrated only when its API, data ownership, operational behavior, security, UI, and rollback path have moved. Splitting code into containers while retaining a shared mutable OFBiz database does not meet that definition.

## 2. Migration principles

1. **Migrate by business capability and user journey.** Do not turn each existing OFBiz component, entity, or service into a network service mechanically.
2. **One front door throughout.** Users keep one origin and coherent navigation while routing changes behind it.
3. **UI and service migrate together.** Each vertical slice includes the user experience, BFF/API, domain logic, data, security, telemetry, and operations needed for that slice.
4. **A service owns its data.** Other services use its API or events, never its tables. Temporary legacy reads must be explicit, read-only, observable, and time-boxed.
5. **Avoid dual writes.** Use local transactions plus an outbox, idempotent consumers, and reconciliation. Where a synchronous legacy update is temporarily unavoidable, define the system of record and recovery procedure.
6. **Prefer asynchronous domain integration.** Use synchronous APIs only when the caller needs an immediate answer. Do not recreate monolithic call chains over HTTP.
7. **Security is part of every slice.** Authentication, authorization, tenant isolation, audit, secrets, input validation, and threat modeling are acceptance criteria, not a final phase.
8. **Compatibility is temporary and measurable.** Every adapter, anti-corruption layer, replicated table, or legacy route has an owner and removal criterion.
9. **Automate infrastructure and delivery.** Azure resources are created only through Terraform, apart from documented bootstrap prerequisites.
10. **Keep rollback cheap.** Feature flags, route weights, backwards-compatible contracts, replayable events, and reconciliation precede traffic migration.

## 3. Target state

### 3.1 Logical architecture

```text
Internet users and clients
          |
          v
Azure Front Door Premium + WAF + TLS + global routing
          |
          v
Azure API Management / web routing facade
     |                              |
     | /api and modern routes       | remaining legacy routes
     v                              v
Modern web shell + BFF          OFBiz compatibility deployment
     |                              |
     +---------- identity -----------+
     |          and transition adapters
     v
Internal Azure Container Apps environment
  +----------------+  +----------------+  +----------------+
  | party/customer |  | product/catalog|  | order          | ...
  | service + DB   |  | service + DB   |  | service + DB   |
  +-------+--------+  +-------+--------+  +-------+--------+
          |                   |                   |
          +----------- Azure Service Bus --------+
                              |
                   events, commands, outbox relay

Shared platform services: Entra ID, Key Vault, App Configuration,
Container Registry, Azure Monitor/Application Insights, Log Analytics,
Managed Grafana dashboards (optional), private DNS and networking.
```

The initial compute recommendation is **Azure Container Apps**, not AKS. It supplies managed revisions, autoscaling, internal ingress, jobs, and workload identities with a smaller operations burden, and its containers run unchanged under local Docker Compose. Adopt AKS only if measured requirements demand Kubernetes-specific scheduling, networking, operators, or platform controls.

### 3.2 Proposed bounded contexts

The final boundaries must be validated through event storming, entity/service-call analysis, and ownership workshops. The following is the working target, not permission to create all services on day one.

| Bounded context | Owns | Important integrations |
| --- | --- | --- |
| Identity access and tenancy | Application roles, tenant membership, authorization policy and audit linkage; credentials remain in Entra ID | All BFFs/services; legacy identity bridge during transition |
| Party and customer | Persons, organizations, contact points, relationships, customer profile | Orders, accounting, marketing, HR |
| Product catalog | Products, variants, categories, catalog content, sellability | Pricing, inventory, ordering, marketing |
| Pricing and promotions | Price lists, rules, promotions, price decisions | Catalog and order capture |
| Inventory and facilities | Stock, reservations, facilities, movements, availability | Order, fulfillment, manufacturing |
| Order capture | Carts, quotes where applicable, sales/purchase orders and lifecycle | Party, pricing, inventory, payment, fulfillment, accounting |
| Fulfillment and shipment | Pick/pack/ship, carriers, shipment status, returns logistics | Inventory, order, customer |
| Billing and invoicing | Invoices, billing accounts, receivables/payables lifecycle | Order, payment, ledger, party |
| Payment | Payment intents, methods/tokens, receipts, refunds and provider integration | Order and billing; never store raw card data |
| General ledger | Accounts, periods, journals, postings and financial controls | Billing, payment, inventory costing, reporting |
| Work and human resources | Work efforts, time, employees, positions and skills; may later split if justified | Party, manufacturing, ledger |
| Manufacturing | BOMs, routings, production runs and material requirements | Catalog, inventory, work, costing |
| Marketing and content | Campaigns and managed business content; media blobs live in object storage | Customer and catalog |
| Notification | Email/SMS/application notifications and templates | Consumes events from all contexts |
| Reporting and analytics | Read models and analytical products, not transactional ownership | Event stream and curated data exports |

Start with a coarse service per proven bounded context. Split only when scaling, release cadence, security, or team ownership provides evidence. A microservice must be independently deployable and own a schema/database; it need not be tiny.

### 3.3 Service internal standard

New services use a consistent but non-invasive template:

- Java 21 with Spring Boot (or the organization-approved runtime), Gradle wrapper, OCI image, health/readiness endpoints, OpenAPI, and structured logs;
- package-by-feature or ports-and-adapters boundaries so domain code does not depend on Azure SDKs or transport details;
- REST/JSON for request/response APIs initially, with versioned contracts and generated clients where useful;
- Azure Service Bus topics/queues for integration events and commands;
- PostgreSQL Flexible Server databases/schemas as the default transactional store, independently credentialed per service;
- an outbox table committed with domain state and a relay that publishes immutable events;
- inbox/idempotency records for consumers and idempotency keys for retryable commands;
- database migrations through Flyway or Liquibase, executed as a controlled deployment job;
- OpenTelemetry traces, metrics, logs, correlation IDs, and business/audit events;
- workload identity in Azure and short-lived/local-only credentials in development.

Shared libraries are limited to cross-cutting technical concerns such as observability, authentication middleware, test fixtures, and event envelopes. Domain models and persistence classes must not be shared between services. Shared libraries are versioned and services can upgrade independently even though their sources live in one repository.

### 3.4 Data architecture

Each service has exclusive write ownership of its database. An Azure Database for PostgreSQL Flexible Server cluster may host several isolated service databases early for cost efficiency, but each service uses separate database credentials and has no grants on another service's data. High-risk or high-scale contexts can move to dedicated servers without changing contracts.

Data moves through four controlled states:

1. **Legacy owned:** OFBiz is authoritative; modern code accesses it only through an anti-corruption API or replicated read model.
2. **Shadowed:** historical data is backfilled and ongoing changes are captured; results are compared but modern state is not user authoritative.
3. **Modern owned:** commands route to the modern service; required legacy views receive events or compatibility writes.
4. **Legacy removed:** legacy reads/writes and synchronization are disabled after retention and reconciliation criteria pass.

Use Azure Service Bus for operational integration, not as a permanent event archive. If long-term replay or high-volume change streams become necessary, add Event Hubs after measuring the requirement. Reporting consumes events/exports into a separate analytical store and never queries all service databases at request time.

Cross-service ACID transactions are replaced by explicit workflows/sagas. Compensating actions, timeouts, retries, and manual recovery queues are defined for each workflow. Financial invariants remain local to the ledger/billing boundary wherever possible.

### 3.5 Web target and incremental composition

The target UI consists of a modern web shell plus capability-owned feature modules. Prefer one coherent application build with route-level lazy-loaded modules in the monorepo initially. Introduce independently deployed micro-frontends only if teams demonstrably require independent UI release trains; runtime UI federation adds failure and security modes that are not needed merely because the backend uses microservices.

The shell owns navigation, layout, accessibility baseline, localization, session state, error handling, design tokens, telemetry, and the migration marker. Each feature owns routes and components for a business capability. Browser code calls a same-origin **backend for frontend (BFF)**, never private services directly. The BFF performs token handling, response composition, CSRF protection where cookies are used, and API-specific authorization enforcement.

During migration, the edge maintains route ownership metadata:

| Route state | User experience | Backend behavior |
| --- | --- | --- |
| `legacy` | Existing OFBiz page in the common navigation | Request routed to OFBiz |
| `hybrid` | Modern shell/page with some legacy-backed operations | BFF calls modern services and explicit legacy adapters |
| `modern` | Modern capability page | BFF calls only modern-owned APIs for the migrated slice |

Legacy pages should be linked through top-level navigation or safe full-page transitions. Embedding OFBiz in an iframe is not the default because it complicates content security policy, cookies, accessibility, navigation, and clickjacking protection. Use an iframe only for a narrowly approved transitional case with explicit `frame-ancestors`, sandbox, and threat review.

### 3.6 Visible modern-page markers

Every page must display its migration state so testers and users can tell which implementation they are exercising:

- the modern shell renders a persistent, accessible badge such as **“Modern”** for `modern` routes and **“Hybrid”** for `hybrid` routes;
- production can use a discreet header badge; non-production adds the service/version and environment in a diagnostic drawer;
- the marker is driven by a typed, build-time route manifest owned in source control, not by URL heuristics or arbitrary query parameters;
- the server/BFF also emits `X-Application-Generation: modern|hybrid|legacy` and OpenTelemetry attributes for automated verification;
- edge middleware strips any inbound client-supplied generation header before setting the trusted response value;
- legacy responses receive a `legacy` value at the edge. A small common-theme extension may render a “Legacy” badge if product owners want symmetrical UI, but modern markers do not depend on modifying every OFBiz page;
- end-to-end tests assert that the visible marker, route manifest, response header, telemetry route owner, and actual backend calls agree.

The badge is not a security control and must not expose internal hostnames, stack traces, tokens, or sensitive build metadata. Only authorized support roles can open detailed diagnostics in production.

### 3.7 Security target

- Microsoft Entra ID provides OIDC/OAuth 2.0 authentication, MFA/Conditional Access, and workforce or external identities as appropriate.
- The web shell uses authorization-code flow with PKCE through the BFF. Prefer secure, `HttpOnly`, `Secure`, `SameSite` session cookies so access tokens are not stored in browser storage.
- Services validate audience, issuer, expiry, tenant, and scopes/roles at every boundary. The BFF being authenticated is not sufficient authorization.
- Managed identities authenticate workloads to Key Vault, Service Bus, App Configuration, databases where supported, and other Azure services. No long-lived production secret is committed or baked into an image.
- Front Door Premium WAF supplies the public edge. Container Apps, databases, Key Vault, Service Bus, and registry use private networking/private endpoints according to environment threat requirements. Only the edge/API gateway is public.
- APIM applies coarse policies, rate limits, request size limits, and routing; domain authorization remains in services.
- Key Vault holds certificates and unavoidable secrets. App Configuration holds non-secret settings and feature flags.
- Every service emits immutable security/audit events containing actor, tenant, action, target, result, timestamp, and correlation ID without logging secrets or regulated payloads.
- Software supply-chain controls include locked dependencies, private approved registries, SBOMs, image/dependency/IaC scanning, image signing/attestation, least-privilege CI federation, and promotion of the same digest across environments.
- The legacy bridge maps Entra identity to OFBiz users server-side and uses signed, short-lived, audience-bound assertions. It must not accept user identity from browser-provided headers. Its removal is a tracked migration deliverable.

### 3.8 Azure deployment topology

Recommended production resources are:

| Concern | Azure service |
| --- | --- |
| Global edge, TLS, WAF | Azure Front Door Premium |
| API facade and strangler routing | Azure API Management, with Front Door origin controls |
| Modern services, BFF, workers, legacy OFBiz container | Azure Container Apps in an internal environment |
| Scheduled/one-off jobs and migrations | Container Apps Jobs |
| Container artifacts | Azure Container Registry |
| Transactional databases | Azure Database for PostgreSQL Flexible Server |
| Messaging | Azure Service Bus Premium for production |
| Identity | Microsoft Entra ID and managed identities |
| Secrets/keys/certificates | Azure Key Vault |
| Runtime configuration/flags | Azure App Configuration |
| Binary business content | Azure Blob Storage with private access |
| Logs, metrics, traces and alerts | Azure Monitor, Log Analytics and Application Insights |
| DNS/network isolation | Azure DNS, VNet integration, private endpoints/private DNS zones |

Use at least development, staging, and production subscriptions or equivalently strong landing-zone separation. Production has independent state, identities, network, data, quotas, alerting, and approval gates. Availability zones, backups, geo-recovery, retention, RTO, and RPO are configured from documented business requirements rather than copied blindly between services.

## 4. Monorepo and developer experience

### 4.1 Intended repository layout

```text
applications/                 # legacy OFBiz during migration
framework/                    # legacy OFBiz during migration
themes/                       # legacy themes during migration
services/
  <bounded-context-service>/
    src/ test/ build.gradle Dockerfile README.md
web/
  shell/
  features/<capability>/
  packages/design-system/
  packages/api-clients/
platform/
  contracts/                  # OpenAPI, AsyncAPI/event schemas and compatibility tests
  service-template/
  observability/
infra/
  modules/                    # reusable Terraform modules
  bootstrap/                  # backend/identity bootstrap, separately controlled
  environments/{dev,staging,prod}/
local-dev/
  docker-compose.yml
  seed/
  hooks/
  smoke-test.sh
docs/
  adr/ runbooks/ migration/
```

OFBiz's existing Gradle build remains usable. A root orchestration script or task runner provides stable commands across Gradle and the web toolchain. At minimum:

- `./modern build` builds OFBiz, all changed services, web assets, and images;
- `./modern test` runs unit, architecture, contract, and relevant integration tests;
- `./modern up` starts the hybrid stack;
- `./modern down` stops it without deleting developer data;
- `./modern reset` clearly warns and recreates local data;
- `./modern smoke` verifies identity, marker correctness, a legacy route, a hybrid route, a modern route, messaging, and health endpoints.

### 4.2 Local topology

Docker Compose is the authoritative integration topology for a developer machine. It starts:

- OFBiz with its legacy database;
- the edge/router, modern web shell, and BFF;
- all services required by the selected profile;
- PostgreSQL with one logical database/credential per service;
- a local Service Bus-compatible development adapter or an explicitly selected Azure development namespace;
- an OIDC development provider seeded with test users and roles, while integration tests also run against an Entra-backed environment;
- OpenTelemetry Collector and lightweight trace/log tooling;
- data-seed and migration jobs.

Profiles keep startup practical: `core` runs infrastructure plus the current slice; `full` runs the entire hybrid application. Local endpoints and contracts match cloud behavior, but emulators are not treated as proof of Azure integration. CI and the shared development environment run Azure-specific tests.

No local command requires production credentials. Secrets come from ignored developer files/credential helpers; CI uses OIDC workload federation. Builds use the approved private Maven/npm/container registries and never persist credentials in image layers, consistent with `docs/migration/dependency-configuration.md`.

### 4.3 CI/CD in a monorepo

Path-aware pipelines calculate affected projects but keep a scheduled full build to detect hidden coupling. Each pull request runs formatting, static analysis, unit tests, architecture rules, API/event compatibility checks, Terraform validation/security checks, container scanning, and relevant integration tests.

Main-branch pipelines create immutable signed images and web artifacts, generate SBOM/provenance, and publish contract versions. Environment deployment uses Terraform plans plus artifact manifests. Promote exact image digests; do not rebuild per environment. Container Apps revisions support canary/blue-green traffic, with automated health and business-metric rollback. Database changes follow expand/migrate/contract and are never rolled back by destructively reverting a schema.

## 5. Terraform design

### 5.1 Structure and ownership

Terraform is split by lifecycle and blast radius rather than put into one state file:

- `bootstrap`: remote state storage, CI federated identities, and prerequisites that cannot create themselves;
- `foundation`: resource groups, networking, private DNS, Log Analytics, shared policy and diagnostic settings;
- `data-platform`: PostgreSQL, Service Bus, storage and backup configuration;
- `application-platform`: Container Apps environment, ACR, APIM, Front Door, Key Vault and App Configuration;
- `workloads`: per-service identities, Container Apps/Jobs, configuration references, database grants, queues/topics/subscriptions, dashboards and alerts.

Reusable modules live under `infra/modules`, have semantic versions/tags inside the monorepo release process, examples, validation, and automated tests. Environment roots supply only environment-specific values. Avoid Terraform workspaces as the sole production isolation mechanism; use distinct backends/state keys and Azure boundaries.

State is encrypted in an Azure Storage account with versioning, soft delete, locking, private access, RBAC, and audit logs. CI authenticates using Entra workload identity federation. Plans run on pull requests and are retained for review; applies require environment protection and separation of duties appropriate to risk. No secret value is passed as a normal Terraform variable if a managed identity or runtime Key Vault reference can avoid it, because sensitive Terraform values still enter state.

### 5.2 Mandatory module behaviors

Every resource module must provide:

- consistent names/tags including owner, system, environment, data classification, and cost center;
- least-privilege identities and role assignments;
- diagnostic settings and actionable alerts;
- private network access and explicit egress controls where required;
- zone redundancy, backups, retention, locks, and purge protection according to tier;
- budgets/quota alerts and autoscaling bounds;
- outputs that do not disclose credentials;
- policy/security scanning and `terraform fmt`, `validate`, and plan tests.

Terraform provisions infrastructure. Application schemas and seed data are owned by versioned migration jobs, not Terraform provisioners.

## 6. Strangler mechanics

### 6.1 Routing and release unit

Front Door protects the public origin and sends application traffic to APIM/routing components. A source-controlled route catalog identifies owner, state (`legacy`, `hybrid`, `modern`), required roles, feature flag, rollback route, and telemetry label for each user route/API. The same catalog generates or validates:

- APIM/edge routing configuration;
- the web shell route manifest and visible marker;
- authorization test cases;
- smoke-test expectations;
- migration dashboards.

Route changes are reviewed and deployed like code. Cohort-, tenant-, and percentage-based canaries use server-evaluated flags. A browser cannot promote itself to a modern route using a query parameter or untrusted header.

### 6.2 Anti-corruption layer

A dedicated legacy adapter presents explicit, versioned endpoints/events around OFBiz services and entities. It translates OFBiz names, status codes, error semantics, IDs, and authentication into the modern ubiquitous language. New services never link OFBiz libraries or query its database directly. The adapter is observable and rate limited and has a planned deletion date per endpoint.

Where OFBiz must consume a modern capability, an OFBiz plugin calls the modern API or consumes integration events behind a small interface. This keeps compatibility code out of core OFBiz and makes later removal visible.

### 6.3 Standard slice lifecycle

Every business slice follows the same repeatable sequence:

1. Map the user journey, actors, authorization, entities, services, ECAs, scheduled jobs, reports, and failure/recovery behavior.
2. Define the bounded-context API/event contracts and identify the single authoritative writer at each transition stage.
3. Add characterization tests and production observability around current OFBiz behavior.
4. Build the service, schema, outbox/inbox, BFF endpoint, and modern UI behind a disabled server-side flag.
5. Backfill historical data with checkpoints and checksums; capture ongoing changes; reconcile counts, totals, invariants, and sampled records.
6. Run shadow reads/calculations without user-visible effects and compare results within agreed tolerances.
7. Enable internal users, then selected tenants/cohorts, then weighted traffic. Display the `Hybrid` marker until all operations for that slice are modern-owned.
8. Switch command ownership once rollback/replay procedures and operational readiness pass. Publish modern events to compatibility consumers.
9. Switch reads and display `Modern`; monitor technical and business service-level indicators through a defined soak period.
10. Remove legacy UI routes, writes, synchronization, adapters, tables/code only after audit/retention obligations and at least one tested rollback window are satisfied.

## 7. Migration phases

Dates should be set after discovery and team-capacity planning. Advancement is gate-based; multiple vertical slices can be in different phases concurrently.

### Phase 0 — Mobilize, measure, and define boundaries

**Objective:** establish facts, ownership, and success measures before creating distributed failure modes.

Work:

- inventory OFBiz routes, services, entity/view models, ECA rules, scheduled jobs, reports, integrations, data volumes, tenants, permissions, and operational procedures;
- capture runtime traces and service/entity call graphs for critical journeys;
- classify data and document compliance, retention, RTO/RPO, latency, throughput, availability, and business invariants;
- run domain/event-storming workshops and record bounded-context ADRs;
- choose the first thin slice based on business value, low financial risk, manageable coupling, and representative architecture;
- baseline lead time, deployment frequency, change failure rate, recovery time, page/API performance, defect rate, infrastructure cost, and key business outcomes;
- identify accountable product/domain owners and an enabling platform team.

Exit gate:

- approved context map and first-slice definition;
- dependency inventory covers direct calls plus controllers, ECAs, scheduled work, views, reports, and integrations;
- security threat model and data classification reviewed;
- measurable migration scorecard and rollback authority agreed.

### Phase 1 — Build the paved road and Azure landing zone

**Objective:** make the secure path the easiest way to create and operate a service.

Work:

- create the proposed monorepo structure, service/web templates, contract registry, ownership rules, and architecture tests;
- implement root `modern` build/up/down/test/smoke commands and hybrid Docker Compose topology;
- build Terraform bootstrap, foundation, network, identity, Container Apps, PostgreSQL, Service Bus, ACR, Key Vault, App Configuration, monitoring, APIM, and Front Door modules;
- create isolated development and staging environments through Terraform;
- implement CI workload federation, immutable artifact promotion, SBOM/signing/scanning, policy checks, canary deployment, and rollback;
- establish OpenTelemetry conventions, SLO templates, alert routing, dashboards, runbooks, and cost attribution;
- prove backup restore, message dead-letter recovery, regional recovery assumptions, and a Container Apps revision rollback.

Exit gate:

- a template service and web route deploy from the monorepo locally and to Azure without manual resource changes;
- private connectivity, managed identity, secrets, telemetry, autoscaling bounds, backups, and alerts are tested;
- `terraform plan` is reproducible and drift detection is active;
- a new service can be created and operated using documented paved-road steps.

### Phase 2 — Establish the secure strangler edge, identity, and UI shell

**Objective:** introduce the permanent entry point before migrating business behavior.

Work:

- place Front Door/WAF and APIM/routing in front of OFBiz with no intended behavior change;
- build the route catalog and verify legacy passthrough, headers, cookies, uploads, redirects, timeouts, and client IP handling;
- integrate Entra ID, BFF sessions, authorization policy, logout, token renewal, and the server-side legacy identity bridge;
- implement the modern shell, design system, shared navigation, localization, accessibility checks, error boundaries, CSP and telemetry;
- implement trusted `Legacy`, `Hybrid`, and `Modern` response classification and visible markers;
- add synthetic tests proving route target, role enforcement, marker accuracy, CSRF protection, and prevention of header/route spoofing;
- roll the shell/edge to internal users, then all users while business pages still route to OFBiz.

Exit gate:

- all traffic uses the common edge and can be returned to direct legacy routing quickly;
- sign-in, sign-out, session expiry, authorization, and tenant isolation pass penetration/security testing;
- every tested route has an accurate marker/header/trace owner;
- legacy behavior and performance remain within baseline tolerance.

### Phase 3 — Deliver a low-risk end-to-end pilot slice

**Objective:** prove the full extraction playbook with a useful, reversible vertical slice.

Recommended candidates are a read-mostly product-catalog search/detail journey or notification preferences, selected after Phase 0. Do not choose ledger posting, payment, or complete order checkout first.

Work:

- create the bounded-context service and database, legacy adapter, event contracts, backfill/reconciliation, modern BFF route, and modern UI feature;
- shadow and compare results against OFBiz;
- canary by internal user/tenant and show `Hybrid` while OFBiz remains authoritative;
- transfer command/read ownership when applicable, update the marker to `Modern`, and remove the pilot's legacy navigation path after soak;
- perform a game day covering modern rollback, event replay, dead letters, database restore, and legacy fallback;
- record actual effort, defects, latency, operational load, and template improvements.

Exit gate:

- the slice is independently deployable and no modern component directly accesses the OFBiz database;
- reconciliation meets defined business tolerances and ownership is unambiguous;
- canary, rollback, support, and security controls have been exercised in production conditions;
- at least one legacy route and its unused compatibility code can be removed.

### Phase 4 — Extract reference/master-data capabilities

**Objective:** move relatively stable capabilities that unblock later transactional domains.

Indicative sequence:

1. product catalog and catalog content;
2. party/customer profiles and contact mechanisms;
3. pricing/promotions;
4. facilities and inventory availability/read models.

For each capability, repeat the standard slice lifecycle. Publish stable events such as `ProductChanged`, `PartyChanged`, and `PriceDecisionPublished`; consumers build local read models rather than synchronously traversing multiple services. Migrate the corresponding search, detail, and maintenance pages concurrently and expose their markers.

Special controls:

- preserve legacy IDs as external references while modern services issue/own identifiers for new records;
- define PII minimization, erasure, retention, and consent behavior for party data;
- test price and inventory results with high-volume golden datasets;
- stop new cross-domain foreign keys and OFBiz view entities from expanding during the migration.

Exit gate:

- modern services are authoritative for the selected capabilities;
- downstream consumers use APIs/events or owned projections, not shared tables;
- migrated screens no longer depend on OFBiz rendering or session state;
- synchronization back to OFBiz is bounded to identified remaining consumers.

### Phase 5 — Extract order-to-cash in controlled subflows

**Objective:** migrate the core commercial journey without a big-bang transaction rewrite.

Suggested subflow order:

1. quote/cart and order inquiry;
2. order capture with modern party/catalog/pricing integrations;
3. inventory reservation;
4. fulfillment/shipment and returns;
5. billing/invoice generation;
6. payment orchestration/refunds;
7. ledger posting integration.

Use saga orchestration for long-running order workflows, with explicit state, deadlines, idempotency, compensation, and human recovery. Separate user acceptance from background completion: return a durable operation/order identifier and expose status rather than holding distributed HTTP transactions open.

Run financial parallel books/comparisons before transferring billing or ledger authority. Reconcile orders, tax, invoice totals, payments, receivables, inventory movements, and journal debits/credits at transaction and aggregate levels. Payment design must use provider tokenization and minimize PCI scope.

UI routes move at subflow granularity. An order detail page may initially be `Hybrid` while modern order data is combined with legacy invoice history; it becomes `Modern` only when its declared operations no longer require legacy runtime calls. The diagnostic drawer lists capability ownership without leaking sensitive data.

Exit gate:

- new orders complete through modern-owned workflows for the enabled cohort;
- no distributed database transaction or unbounded synchronous call chain is required;
- reconciliation, audit, recovery queues, month-end/financial controls, and rollback have business sign-off;
- legacy order-to-cash writes are disabled for migrated cohorts and remaining compatibility feeds are measured.

### Phase 6 — Extract remaining complex domains and integrations

**Objective:** migrate accounting remainder, manufacturing, work/HR, marketing/content, reporting, batch work, and external integrations.

Work:

- prioritize by remaining OFBiz dependency and business value, not directory order;
- replace OFBiz ECAs with explicit domain handlers/policies and document whether execution is synchronous or event-driven;
- replace scheduled OFBiz services with owned workers or Container Apps Jobs, including singleton/concurrency and replay controls;
- move files/media to Blob Storage with malware scanning, lifecycle, authorization, and immutable retention where required;
- build analytical projections from events/exports and reconcile official reports before switching them;
- migrate administrative UIs and operational tools or replace them with supported platform tools;
- contract-test every external partner and provide parallel endpoints/certificates during cutover.

Exit gate:

- each remaining business capability has a modern owner and UI/API route;
- no critical ECA, scheduler, report, integration, tenant operation, or support workflow exists only in OFBiz;
- remaining OFBiz traffic is zero or explicitly approved archival/read-only traffic.

### Phase 7 — Decommission OFBiz and transitional infrastructure

**Objective:** remove the monolith safely and realize the operational benefit of migration.

Work:

- freeze OFBiz writes, complete final change capture, reconcile, archive, and obtain domain/compliance sign-off;
- maintain a time-bounded, audited read-only archive or export if retention requires it;
- remove legacy routes, identity bridge, anti-corruption endpoints, reverse replication, legacy database grants, OFBiz images and runtime volumes;
- remove unused APIM policies, Service Bus entities, flags, secrets, certificates, dashboards, alerts, and Terraform resources through reviewed plans;
- prove that backup/restore, disaster recovery, security incident response, and operational ownership work without OFBiz;
- update documentation, support procedures, data lineage, threat models, and cost baselines.

Exit gate:

- zero production requests, jobs, integrations, or writes require OFBiz for the agreed observation period;
- retention and legal/audit access are satisfied independently;
- legacy infrastructure and credentials are destroyed through auditable procedures;
- the decommission decision is signed by product, finance/data owners, security, and operations.

## 8. Workstreams that run through every phase

### Architecture and governance

- ADRs record service boundaries, data ownership, sync/async decisions, and exceptions.
- CODEOWNERS aligns code with domain/platform owners while encouraging cross-team review.
- Automated rules prevent service-to-service database access, cyclic build dependencies, and domain-model sharing.
- A lightweight architecture forum resolves cross-context contracts; it does not become a central approval bottleneck.

### Testing and quality

The test pyramid includes domain unit tests, architecture tests, database migration tests, API/event schema compatibility, consumer-driven contracts, component tests with real PostgreSQL and messaging containers/adapters, hybrid Compose tests, Azure integration tests, UI accessibility/security tests, and a small critical-path end-to-end suite. OFBiz characterization and reconciliation tests remain until their slice is removed.

Performance tests cover edge, BFF, service, database, messaging backlog, and complete journeys. Resilience tests exercise timeouts, retries with jitter, circuit breakers where appropriate, poison messages, partial Azure dependency failure, revision rollback, and recovery from backup.

### Operations and observability

Every slice defines service-level indicators and objectives, dashboards, alerts, runbooks, ownership, on-call escalation, capacity bounds, and cost expectations before production traffic. Traces propagate W3C context through edge, BFF, services, events, and legacy adapter. Logs are structured and redact secrets/PII. Business metrics and reconciliation alarms are as important as CPU and HTTP error rates.

### Change and user adoption

Feature flags and route cohorts support training and gradual rollout. The visible page marker gives users/testers an unambiguous feedback channel; feedback includes route ID, generation, correlation ID, and non-sensitive version automatically. Support staff receive tooling to identify whether a request used legacy, hybrid, or modern paths.

## 9. Definition of done for a migrated slice

A slice may be reported as modern only when all of the following are true:

- its route is classified `modern` in the reviewed route catalog and displays the trusted marker;
- the modern service is the authoritative writer for its data;
- its UI does not render through OFBiz and its normal path does not call a legacy adapter;
- APIs/events are versioned, documented, authorized, rate/size limited, and contract tested;
- backfill and continuous reconciliation pass documented business invariants;
- outbox/inbox, retry, dead-letter, idempotency, and manual recovery behavior are tested;
- telemetry, audit, SLOs, alerts, runbooks, capacity, cost, backup/restore, and DR expectations are operational;
- threat modeling, dependency/image/IaC scanning, privacy controls, and penetration findings are resolved to policy;
- canary and rollback have been exercised;
- legacy routes/writes are disabled and removal work has an owner and deadline.

## 10. Measures and decision gates

Track the following by slice and for the program:

- percentage of user routes and production requests classified legacy/hybrid/modern;
- percentage of authoritative writes and scheduled jobs outside OFBiz;
- number and age of legacy adapter endpoints, reverse feeds, flags, and direct legacy reads;
- reconciliation mismatch rate and unresolved recovery items;
- availability, latency, error rate, message age/backlog, and business completion rate;
- deployment frequency, lead time, change failure rate, and recovery time;
- security findings, authorization failures, audit completeness, and secret age;
- infrastructure cost per environment and per business transaction;
- user task success, accessibility findings, and support tickets by route generation.

At the end of each phase, continue only if security, data correctness, operational readiness, and rollback gates pass. If service count or synchronous dependencies grow faster than independently owned capabilities, pause extraction and revisit boundaries rather than continuing a distributed monolith.

## 11. Principal risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Distributed monolith replacing the in-process monolith | Coarse bounded contexts, database ownership tests, async integration, dependency graph monitoring, no shared domain libraries. |
| Hidden ECA/scheduler/report behavior is missed | Static inventory plus runtime tracing, characterization tests, shadow operation, business reconciliation. |
| Data divergence during coexistence | One writer at a time, outbox/CDC, idempotency, checksums/invariants, replay and repair tooling. |
| Identity split or privilege escalation | Entra/BFF pattern, server-side signed legacy bridge, centralized policy vocabulary with service enforcement, automated tenant/role tests. |
| Inconsistent UI and confusing navigation | Shared shell/design system, route-level vertical slices, same origin, visible trusted markers, accessibility and journey tests. |
| Excess Azure/platform complexity | Container Apps before AKS, paved-road templates, managed services, cost limits, platform team, evidence-based additions. |
| Long-lived temporary adapters | Endpoint owner, metrics, deadline/removal gate, program dashboard, prohibit new consumers. |
| Big-bang financial cutover | Subflow migration, parallel calculations/books, transaction and aggregate reconciliation, finance sign-off, explicit recovery. |
| Monorepo pipelines become slow | Affected-project builds, remote caches, parallel jobs, hermetic builds, scheduled full verification. |
| Terraform state/blast radius becomes unsafe | Layered state, environment separation, federation, reviewed plans, policy checks, locks/versioning, tested recovery. |
| Local environment diverges from Azure | Same OCI images/contracts/migrations, Compose profiles, Azure integration environment, automated smoke/conformance tests. |

## 12. Immediate next actions

1. Approve or revise the architectural choices in this plan through ADRs: Container Apps, APIM/Front Door routing, Entra+BFF identity, PostgreSQL, Service Bus, and the initial UI composition model.
2. Complete Phase 0 inventory and select the first vertical slice using evidence rather than component names.
3. Create a thin platform backlog for the route catalog/markers, hybrid local stack, service template, Terraform landing zone, identity bridge, observability, and CI federation.
4. Define the first slice's business invariants, authorization matrix, SLOs, data reconciliation rules, and rollback decision before implementation.
5. Deliver Phase 1 and Phase 2 foundations, then use the pilot to validate and revise this plan before scaling service creation.
