# OFBiz migration plan: strangler migration to Azure microservices

## 1. Purpose, scope, and guiding decisions

This plan migrates the modular OFBiz monolith described in `OFBIZ_current_architecture.md` to a modern, Azure-hosted microservices architecture without a big-bang rewrite. The migration uses a strangler pattern: the legacy application remains operational behind a common edge while business capabilities and their user journeys are replaced one vertical slice at a time.

The source remains in one repository. Each modern service and UI route is independently buildable, testable, versioned, deployable, observable, and responsible for its own data. A developer must be able to build and start the complete useful system—modern components plus the legacy fallback—on a development machine without an Azure subscription.

The following are firm principles for the migration:

1. **Migrate business capabilities, not technical layers.** Each production increment includes API/service behavior, owned data, security, observability, and the corresponding UI route.
2. **Keep the system releasable.** Every phase has explicit entry/exit criteria, automated tests, backward compatibility, rollback, and no long-lived half-cut-over state.
3. **One front door and one user experience.** Users retain stable URLs and navigation while routing moves behind the edge.
4. **Modern pages are unmistakable.** Every modern route displays a consistent, accessible marker sourced from trusted route metadata; legacy pages do not display it.
5. **One writer per datum.** During transition, every entity/field has an explicitly declared system of record. Avoid application-level dual writes.
6. **No new coupling to OFBiz internals.** Modern code uses an anti-corruption layer and published contracts, never the OFBiz database or `Delegator` directly.
7. **Secure by default.** Entra ID, least-privilege managed identities, private service connectivity, centrally enforced edge policy, workload authorization, and auditable data access are part of the first slice.
8. **Infrastructure is code.** Azure resources, policies, identities, alerts, dashboards, and environment configuration are created through reviewed Terraform.
9. **Prefer managed platform services.** Use Azure Container Apps initially rather than operating Kubernetes. Revisit AKS only when measured requirements cannot be met by Container Apps.
10. **Optimize for removal.** A slice is complete only when its replaced legacy entry points, jobs, writes, and eventually data structures can be retired.
11. **Keep one authoritative build graph.** The root `./gradlew build` command builds and verifies all tracked legacy and modern code. New languages and toolchains integrate behind Gradle tasks rather than creating a second aggregate build.
12. **Protect new and changed code with tests.** Sonar's coverage on New Code must remain at or above 80% for newly introduced or modified executable code. This improves the system incrementally without making the existing legacy codebase's aggregate coverage a migration blocker.

## 2. Current-state constraints that shape the migration

The existing application is one JVM containing all OFBiz components. Modules share the service dispatcher, entity engine, database transactions, process-local caches, scheduled jobs, sessions, and a cross-domain relational model. XML service, entity, controller, widget, and ECA definitions contain important business behavior that is not visible from Java dependencies alone.

Consequently, copying Java classes into services is not a safe decomposition strategy. Before extracting a capability, the team must inventory:

- its OFBiz services and direct callers;
- entities, view entities, field-level reads/writes, and implicit relations;
- service ECA, entity ECA, scheduled jobs, MiniLang, scripts, and data loaders;
- controller routes, screens, forms, templates, permissions, and session assumptions;
- transaction boundaries and invariants spanning domains;
- reports, integrations, exports, and operational/admin procedures.

The current repository contains no tracked microservice, modern UI, Terraform, or production Azure deployment. These are introduced incrementally by this plan. Ignored build remnants under `services/` and `web/` are not a starting implementation.

## 3. Target state

### 3.1 Logical architecture

```text
Users and external clients
          |
Azure Front Door Premium + WAF + TLS + global routing
          |
          +-----------------------+
          |                       |
  Modern web shell          Legacy OFBiz adapter/origin
  and route bundles         (only remaining routes)
          |
  Route BFFs / API Management
          |
  +-------+---------+------------------+
  |                 |                  |
Party/Identity   Catalog/Inventory   Order, Invoice, ... services
  |                 |                  |
service-owned    service-owned       service-owned
PostgreSQL DB     PostgreSQL DB       PostgreSQL DB
  +-----------------+------------------+
                    |
            Azure Service Bus
       events, commands, workflows

Cross-cutting: Entra ID, Key Vault, managed identities, ACR,
Azure Monitor/Application Insights, Log Analytics, OpenTelemetry
```

Azure Front Door Premium is the public entry point. It terminates public TLS, applies WAF and rate-limit policies, and routes stable paths to either the modern origin or the OFBiz origin. Origins accept traffic only through approved private connectivity or validated Front Door traffic. Azure API Management (APIM) is the governed API edge for public/partner APIs and may front internal APIs where policy adds value; it is not used as a substitute for service design.

Modern workloads run as independent Azure Container Apps in an internal Container Apps environment. Each has its own container image, managed identity, scaling policy, health probes, deployment revision, configuration, and telemetry. Jobs use Container Apps Jobs where an event-triggered or scheduled workload is a better fit than a continuously running service. The legacy OFBiz image is initially deployed alongside them as a strangler origin and is scaled/retired as routes move.

### 3.2 Proposed bounded contexts and deployable units

The final boundaries must be validated through domain workshops and dependency evidence. The following is the initial context map, not an instruction to create all services immediately:

| Bounded context | Primary responsibilities | Likely OFBiz source areas |
| --- | --- | --- |
| Identity access bridge | Entra identity mapping, application roles/permissions, transitional OFBiz session/token exchange | `security`, `securityext`, party user-login relations |
| Party | People, organizations, roles, relationships, contacts | `party` |
| Product catalog | Products, categories, features, pricing inputs, catalog browsing | `product` |
| Inventory and facility | Facilities, stock, reservations, availability, movements | `product` facility/inventory |
| Order management | Carts, quotes, sales/purchase orders, status lifecycle, returns | `order` |
| Fulfilment | Pick/pack/ship and shipment integrations | product shipment and order fulfilment behavior |
| Pricing and promotions | Price calculation, rules, promotions | `product` pricing/promotion |
| Invoicing | Invoice lifecycle, line items, adjustments, tax results | `accounting` invoice/billing |
| Payments | Payment intents, methods, provider integrations, reconciliation | `accounting` payment/gateway |
| General ledger | Accounting postings, periods, ledger and financial reporting | `accounting` ledger |
| Work management | Projects, work efforts, calendars, time sheets | `workeffort` |
| Manufacturing | BOM, routing, MRP, production runs | `manufacturing` |
| Human resources | Employment and HR workflows | `humanres` |
| Marketing and CRM | Campaigns, opportunities, SFA workflows | `marketing` plus selected party behavior |
| Content | Content, documents, surveys, communication artifacts | `content` |
| Notifications | Email and other outbound notifications, templates, delivery status | common/content communication services |

Do not create one service per OFBiz component or entity. A bounded context may begin as one deployable service and split only when team ownership, scaling, security, or release evidence warrants it. Conversely, accounting should not be extracted as one large service if invoice, payment, and ledger consistency boundaries prove distinct.

### 3.3 Interaction model

Use synchronous HTTP/JSON APIs for queries and operations that require an immediate response. Define APIs with OpenAPI, generate/validate clients where practical, use explicit timeouts, and make mutating calls idempotent. Do not create deep synchronous call chains; compose user-specific results in a BFF or use local projections.

Use Azure Service Bus topics/subscriptions for integration events and queues for asynchronous commands. Events are versioned facts in past tense, carry correlation/causation IDs, and contain no secrets. Consumers are idempotent and use dead-letter handling with operational replay procedures.

Every service that changes state writes its domain data and an outbox record in one local transaction. A relay publishes the outbox to Service Bus; consumers maintain inbox/deduplication records. Cross-service workflows use explicit sagas/process managers with compensation and timeouts, not distributed transactions. Event schemas and API contracts are compatibility-tested in CI.

During strangulation, an **OFBiz anti-corruption adapter** exposes only the legacy operations and change feeds needed by modern slices. It translates modern contracts to named OFBiz services and maps legacy concepts/errors to the new domain model. It is the only modern component allowed to know OFBiz service names or legacy data shapes. It must not expose generic entity CRUD.

### 3.4 Data architecture

The target is database-per-service ownership on Azure Database for PostgreSQL Flexible Server. Smaller services may initially use separate databases on a shared PostgreSQL server per environment; high-risk or high-scale contexts can receive dedicated servers. Database credentials are not shared, and a service identity can access only its database. Services never join another service's tables.

Azure Cache for Redis may be introduced for measured cache/session needs, never as the system of record. Blob Storage holds documents and bulk exports. Analytics receives curated events or change data in a separate pipeline; operational services are not queried directly for cross-domain reporting.

Each extraction follows a controlled ownership sequence:

1. document the current fields, invariants, consumers, volumes, retention, and classification;
2. declare ownership at entity/field/operation level in a migration ledger;
3. create the service schema and repeatable migration scripts;
4. backfill through a resumable, checksummed migration job;
5. continuously synchronize subsequent legacy changes via the adapter/outbox/CDC mechanism;
6. compare counts, aggregates, sampled records, and domain invariants;
7. move reads behind a feature flag and observe;
8. quiesce affected writes, drain synchronization, and switch the single writer;
9. publish modern events/projections to remaining legacy consumers;
10. after the rollback window, remove legacy writes and later archive/drop obsolete data under an approved retention process.

Database-level CDC can be a temporary capture mechanism, but raw legacy table changes must be translated into domain events before broad consumption. No service gets permanent read access to the OFBiz schema. Bidirectional synchronization is time-boxed, monitored, and never the steady state.

### 3.5 UI target and incremental composition

The target UI is a modern web shell with shared navigation, design system, accessibility, localization, authentication, error handling, telemetry, and a route registry. Migrate **routes/user journeys**, not individual visual controls scattered across old pages. A route bundle is owned and released with its capability or by a clearly named UI team with a contract to that capability's BFF.

The preferred starting composition is build-time route packages in the monorepo, producing one coherent web deployment. This avoids premature runtime micro-frontend complexity. Independently deployed micro-frontends may be introduced later only where independent release ownership is proven; they must still use the shell's versioned contracts, CSP, dependency isolation, error boundary, and integrity controls.

Modern browser code calls same-origin BFF endpoints. It does not call internal services directly and does not store access tokens in local storage. The BFF validates the Entra session/token, enforces authorization, applies CSRF protection where cookies are used, and forwards a constrained user/workload identity. Content Security Policy, secure cookies, HSTS, dependency scanning, SRI where applicable, and output encoding are enforced centrally.

The edge route registry sends a migrated route to the modern shell and any unmigrated route to OFBiz. Navigation remains consistent across both. Where a full route cannot yet move, add a safe link/redirect rather than embedding authenticated OFBiz pages in iframes.

#### Required modern-page marker

Every modern route must display a persistent marker in the shared shell, initially labelled **Modern experience**. It must:

- appear in a consistent header location and include accessible text, not color alone;
- be driven by the shell's build-time route manifest (`experience: modern`), not by query strings or arbitrary backend content;
- include a machine-readable attribute such as `data-experience="modern"` for end-to-end tests and support diagnostics;
- optionally expose service/UI version and correlation information in a support popover without exposing secrets;
- be absent from legacy pages, except for an optional clearly different “Legacy experience” support marker;
- remain enabled in production; users may not dismiss it globally during migration;
- be covered by route-manifest validation, component tests, accessibility tests, and an end-to-end assertion for every modern route.

The route manifest is also the authoritative input for Front Door routing configuration/tests, navigation, feature flags, ownership, and rollback. CI fails if a modern route lacks its marker metadata, owner, BFF/API contract, authorization policy, or end-to-end test. This makes the visible marker a reliable statement about the complete vertical slice, not merely a new visual theme over legacy behavior.

### 3.6 Identity and security target

Microsoft Entra ID is the user identity provider. Separate app registrations represent the web/BFF, governed external APIs, and automation clients. Human access uses authorization code with PKCE; workloads use managed identities and OAuth client credentials only where managed identity is unavailable. Azure RBAC controls Azure resources; application roles/permissions control business operations. Tenant choice (workforce versus external tenant) must be confirmed before implementation based on actual user populations.

During transition, an identity bridge maps the immutable Entra subject/tenant pair to OFBiz `UserLogin`/Party records. The bridge supports audited just-in-time mapping or controlled provisioning and a short-lived, server-side OFBiz session exchange for legacy navigation. Passwords are not synchronized to new services. The common edge prevents clients from bypassing the identity path. Authorization is re-evaluated server-side for every API; the UI marker and route visibility are never authorization controls.

Secrets and certificates reside in Key Vault and are accessed through managed identities. Public access is disabled where supported. Private endpoints/private DNS protect PostgreSQL, Key Vault, ACR, Storage, and Service Bus in production. Images run as non-root, use read-only filesystems where possible, have resource limits, emit software bill of materials, and are vulnerability-scanned and signed/attested before promotion.

Threat modeling is required per vertical slice. CI includes secret, dependency, container, IaC, and SAST scanning; deployment includes policy checks. Logs redact credentials, tokens, personal data, and payment data. Audit events are immutable enough for the applicable retention and compliance requirements.

### 3.7 Azure deployment topology

Each environment has an isolated resource group set and identities. Production is isolated from non-production subscriptions. A typical environment contains:

- Azure Front Door Premium, WAF policy, DNS, and certificates;
- APIM for governed external APIs;
- an internal Azure Container Apps environment and Container Apps Jobs;
- ACR with immutable release tags and retention policy;
- PostgreSQL Flexible Server databases, backups, point-in-time restore, and private DNS;
- Service Bus Premium (or Standard initially where requirements allow) with queues/topics, DLQs, and duplicate detection;
- Key Vault, Storage/Blob, and optional Azure Managed Redis;
- Log Analytics, Application Insights/Azure Monitor, dashboards, action groups, alerts, and budgets;
- virtual networks, delegated subnets, private endpoints, NAT/egress control, and private DNS zones.

Availability-zone redundancy, geo-recovery, RPO/RTO, capacity, and data residency are environment-specific decisions recorded before production. The default production design uses zone-redundant services where available, tested PostgreSQL restore, infrastructure recreation from Terraform, and runbooks for region failover. Multi-region active/active is not assumed because it adds data-consistency complexity; adopt it only from explicit availability requirements.

### 3.8 Monorepo target structure

Introduce the following structure gradually, preserving existing OFBiz paths until retirement:

```text
applications/                 # legacy OFBiz applications during migration
framework/                    # legacy OFBiz framework during migration
themes/                       # legacy themes during migration
services/
  ofbiz-adapter/
  party-service/
  catalog-service/
  .../
web/
  shell/
  routes/
    party/
    catalog/
  packages/
    design-system/
    auth/
contracts/
  openapi/
  events/
  compatibility/
infra/
  bootstrap/                  # state storage/subscription bootstrap
  modules/                    # reusable, versioned Terraform modules
  environments/
    dev/
    staging/
    prod/
local-dev/
  compose.yaml
  config/
  fixtures/
docs/
  architecture/
  decisions/
  migration/
```

The existing root Gradle wrapper remains the authoritative build entry point. `./gradlew build` must build and verify every tracked module: the legacy OFBiz runtime, all modern services and BFFs, the web shell and route packages, shared web packages, generated/validated API and event contracts, migration tooling, and static validation of Terraform and local-development configuration. Every new source-bearing directory must be registered in the Gradle project/task graph in the same change that introduces it. The root `build` task depends on each module's lifecycle task so a successful root build proves that no code module was omitted.

Gradle orchestrates non-JVM tools rather than replacing them. Frontend Gradle projects use a pinned Node.js and package-manager version, invoke reproducible/frozen dependency installation, compile bundles, run tests/lint/type checking, and publish their reports to stable build paths. Contract and infrastructure projects similarly expose generation, compatibility, format, lint, and validation tasks. Terraform validation uses a pinned tool provisioned or invoked reproducibly by the Gradle task; it never applies infrastructure during `build`.

Use a small root developer command (`./modern` or equivalent) for environment-oriented workflows over Gradle, Docker Compose, database migrations, fixtures, and logs. It must provide at least `bootstrap`, `build`, `test`, `up`, `down`, `logs`, and `smoke`, but `./modern build` must delegate to `./gradlew build` and may not maintain a separate module list or weaker definition of success. The command and Compose profiles allow a developer to start the full hybrid system or a focused slice.

Pin JDK, Node/package-manager, Terraform, and container tooling versions. Cache dependencies but never commit generated build output. CI may use path-aware jobs for fast feedback, but the required whole-repository gate runs the root Gradle build and cannot omit unchanged modules. A scheduled/full hybrid runtime test detects deployment and startup coupling. A single repository and aggregate build do not imply a single release: each deployable has an image/version and ownership metadata, while an environment manifest records the compatible set promoted together.

The existing Sonar analysis remains whole-repository analysis. Its authoritative CI job runs `./gradlew build sonar` (or separate root Gradle invocations with identical checked-out sources) after all language reports exist. Sonar source, test, exclusion, coverage, and report configuration must be extended whenever a module is added, including `services/`, `web/`, `contracts/`, `infra/`, and `local-dev/` where relevant. JVM coverage, frontend coverage/test results, and supported external analyzer reports are generated through Gradle and supplied to Sonar. An automated build-registration check fails if a tracked source directory is neither in the root build graph nor intentionally excluded with a documented reason.

### 3.9 Local development target

The local stack uses Docker Compose (or a compatible container runtime) and mirrors production contracts without requiring Azure:

- legacy OFBiz container;
- modern web shell and BFF;
- selected modern services;
- PostgreSQL with separate databases/users per service;
- a Service Bus-compatible abstraction using an emulator only if it meets test needs, otherwise a local broker behind the same messaging port and adapter;
- Azurite for Blob Storage behavior;
- a local OIDC provider or deterministic development authentication profile, never production credentials;
- OpenTelemetry collector and optional local trace/log UI;
- seed/fixture and migration jobs.

Production code depends on narrow ports for messaging, storage, identity claims, clocks, and external providers so local substitutes do not leak into domain logic. CI must also run periodic integration tests against real ephemeral Azure resources for behaviors local emulators cannot prove.

`./modern up` starts the hybrid default, waits on health checks, seeds deterministic data, and prints the one local URL. `./modern smoke` verifies login, one legacy route, every completed modern route and marker, API health, messaging, and data isolation. Startup should be idempotent; state reset must be an explicit destructive command.

## 4. Migration control system

Before extracting domains, maintain four version-controlled artifacts:

1. **Capability map:** business capabilities, owners, users, criticality, volumes, dependencies, and candidate boundaries.
2. **Migration ledger:** route/API/entity/field/job/integration ownership, current writer, synchronization mode, cutover state, rollback window, and retirement status.
3. **Route manifest:** path, experience (`legacy` or `modern`), UI owner, required roles, BFF, feature flag, marker metadata, and fallback route.
4. **Contract catalog:** OpenAPI and event schema versions, producers/consumers, compatibility policy, and deprecation dates.

Use feature flags for cohort-based routing and behavior, but give every flag an owner and removal date. Route changes are canaried by internal users, then small cohorts, then broader traffic. A rollback returns routing to the legacy path only while the data ownership plan explicitly supports it; edge rollback alone is unsafe after irreversible writes.

For each slice, define service-level indicators and objectives for availability, latency, correctness, event lag, DLQ depth, and business outcomes. Correlation IDs propagate from Front Door through BFF, services, events, and the OFBiz adapter. OpenTelemetry supplies traces, metrics, and structured logs. Alerts must be actionable and paired with runbooks.

## 5. Phased migration

Durations are deliberately omitted until discovery establishes team capacity and domain complexity. Phases may overlap where their entry criteria are met, but phase gates must not be skipped.

### Phase 0 — Baseline, governance, and domain discovery

**Goal:** establish facts, ownership, success measures, and migration safety before adding distributed-system complexity.

**Status:** Complete and approved on 2026-09-24. Slice-specific production measurements remain mandatory inputs. A documented Phase 4 exception permits discovery and implementation with delivery assignments temporarily open, but production traffic still requires named operational accountability.

Work:

- approve measurable goals: deployment frequency, lead time, recovery time, defect rate, availability, performance, and target Azure cost envelope;
- inventory OFBiz services, entities, ECAs, scheduled jobs, controllers, permissions, reports, and external integrations with static analysis plus runtime traces;
- build the capability map and dependency graph; identify transaction/invariant boundaries and high-change/high-pain areas;
- classify data and define retention, residency, privacy, payment, and audit obligations;
- define RPO/RTO, availability tiers, traffic/volume forecasts, and acceptable migration downtime per capability;
- perform event-storming/domain workshops and validate initial bounded contexts;
- create architecture decision records for compute, identity tenant, messaging, database tenancy, API style, UI composition, and regional topology;
- define team ownership and on-call responsibility for platform and first vertical slice;
- baseline OFBiz behavior with characterization, performance, and critical-journey tests.

Exit criteria:

- capability map, migration ledger skeleton, initial context map, and prioritized slice backlog are reviewed by business and engineering owners;
- critical legacy journeys have automated characterization tests and production baselines;
- security/compliance and SRE requirements are explicit;
- the first slice is selected using business value, bounded data, low blast radius, and enough complexity to validate the platform.

Repository implementation and evidence for this phase live under `docs/migration/phase0/`. The Gradle tasks `generatePhase0Inventory` and `verifyPhase0Inventory` generate and drift-check static evidence. The Phase 0 README is the authoritative completion checklist; repository-derived work does not substitute for the production measurements and stakeholder approvals identified there.

### Phase 1 — Monorepo and local-development foundation

**Goal:** make modern development reproducible before any production routing changes.

Work:

- create the `services/`, `web/`, `contracts/`, `infra/`, and `local-dev/` source layout;
- extend `settings.gradle` and the root lifecycle so `./gradlew build` includes every legacy and modern service, UI, contract, infrastructure-validation, and migration-tool module;
- add Gradle adapters for pinned Node/package-manager and Terraform tooling, aggregate test/coverage reports, and a build-registration architecture test;
- extend the root Sonar configuration to include all new source/test roots and reports, configure a minimum 80% coverage-on-New-Code quality gate, and make `./gradlew build sonar` the required whole-codebase CI gate;
- add the root `./modern` workflow, with `./modern build` delegating to `./gradlew build`, plus the pinned toolchain, Compose stack, deterministic fixtures, and health-based startup;
- create service and UI templates with secure defaults, health/readiness endpoints, structured logging, OpenTelemetry, migrations, tests, container hardening, and ownership metadata;
- implement contract linting and compatibility tests;
- add a minimal web shell, design system, route registry, accessible modern marker, error boundary, and telemetry;
- containerize the legacy OFBiz application for the hybrid local stack without changing its behavior;
- add optional path-aware CI for fast feedback plus a required full `./gradlew build sonar` gate and a full hybrid startup/smoke test.

Exit criteria:

- a clean development machine can run `./gradlew build`, `./modern bootstrap`, `./modern build`, `./modern up`, and `./modern smoke` from documented prerequisites;
- `./modern build` delegates to `./gradlew build`, and a deliberate compile/test failure in any legacy or modern module fails both commands;
- the registration test demonstrates that all tracked source roots participate in the aggregate build or have an explicit reviewed exclusion;
- the Sonar quality gate fails a change when coverage on newly introduced or modified code is below 80%;
- the local URL serves a shell test route with the Modern experience marker and at least one proxied legacy route without that marker;
- CI proves the whole codebase with root Gradle/Sonar analysis and proves the hybrid stack with its smoke test;
- no production application traffic has changed.

### Phase 2 — Terraform Azure landing zone and delivery platform

**Goal:** create reproducible, secure non-production infrastructure and promotion pipelines.

**Status:** Repository implementation complete on 2026-09-24. Azure acceptance is explicitly deferred because no Azure subscription or infrastructure is available; no cloud resource, deployment, recovery exercise, or Phase 2 Azure exit criterion is claimed as verified. See `docs/migration/phase2/`.

Work:

- implement a one-time Terraform bootstrap for remote state in Azure Storage with versioning, locking, RBAC, soft delete, and separate state per environment/component;
- build reviewed modules for resource groups, networking/private DNS, Container Apps, ACR, PostgreSQL, Service Bus, Key Vault, Storage, observability, APIM, Front Door/WAF, budgets, and alerts;
- configure providers and environments explicitly; do not use Terraform workspaces as the sole environment isolation mechanism;
- use workload identity federation from CI to Azure—no long-lived client secret;
- run format, validate, lint, security/policy checks, and `terraform plan` on pull requests; require approval and retained plans before protected-environment apply;
- separate infrastructure deployment from application image promotion while recording compatible versions;
- implement image build, SBOM, signing/attestation, vulnerability gates, ACR push, deployment revisions, smoke tests, canary, and automatic health rollback;
- provision ephemeral integration environments per pull request where cost permits, with TTL cleanup and budgets;
- test backup restoration, state recovery, Key Vault recovery, and infrastructure recreation.

Exit criteria:

- dev and staging environments are reproducibly created from Terraform with no manual portal-only resources;
- all public ingress passes through Front Door/WAF; data/platform services use private access in staging;
- identities are least-privilege and secretless where possible;
- a sample service and shell deploy through the promotion pipeline with traces, alerts, rollback, and cost reporting;
- production creation remains gated until reliability/security review.

### Phase 3 — Edge strangler, identity bridge, and OFBiz anti-corruption layer

**Goal:** introduce the seam that permits safe route-by-route replacement.

**Status:** Complete on 2026-09-24. Repository implementation and local verification passed. Azure/Entra deployment evidence was explicitly waived by project decision on 2026-09-24 and is not represented as successfully exercised. See `docs/migration/phase3/`.

Work:

- place the current OFBiz deployment behind Front Door as the default/fallback origin while preserving public URLs and redirects;
- deploy the modern shell/BFF test route and route it by explicit manifest entry;
- integrate Entra login, role/group claims, BFF session handling, logout, CSRF defenses, and identity audit events;
- implement the Party/UserLogin mapping and short-lived server-side exchange needed to traverse into legacy routes without password replication;
- prevent direct public access to legacy and modern origins; validate forwarded-host/proxy headers and trusted-origin rules;
- build the narrowly scoped OFBiz adapter using published REST endpoints or a purpose-built OFBiz component that calls named services—not direct database access;
- add end-to-end correlation, distributed traces across the adapter, rate limits, timeouts, circuit breakers, bulkheads, and sanitized errors;
- implement route-level cohort flags and a tested fallback procedure;
- threat-model session fixation, confused deputy, token replay, privilege drift, open redirects, and legacy bypass.

Exit criteria:

- a user signs in once and navigates securely between modern test and legacy routes;
- authorization parity tests cover representative roles and denied access;
- all routes are observable and traceable through the common edge;
- the route manifest and marker cannot claim a route is modern unless its full test/security gate passes;
- failure of the modern test route can be isolated and routed back without impairing legacy OFBiz.

### Phase 4 — First production vertical slice

**Goal:** prove the complete extraction and UI migration method with a bounded, reversible capability.

**Status:** Started on 2026-09-24 with Product category browse/search as the approved slice. Discovery status and evidence live under `docs/migration/phase4/`; no production readiness or traffic cutover is yet claimed.

Choose the slice after Phase 0. A read-heavy catalog/search journey or another low-transaction capability is a likely candidate, but the plan must not predetermine it without dependency evidence.

Work:

- write the slice charter: users, route, APIs, data fields, current and target writer, invariants, events, permissions, SLOs, cutover, and rollback;
- capture legacy behavior in contract and golden-master tests;
- implement the domain service, owned schema, migrations, outbox/inbox, APIs/events, authorization, telemetry, and operational endpoints;
- build the matching modern UI route and BFF, using the shared shell/design system and Modern experience marker;
- backfill and synchronize data, run shadow reads, and compare results automatically;
- test accessibility, browser security, load, failure modes, DLQ/replay, backup/restore, and support procedures;
- release to staff, then a small cohort, then increasing traffic while comparing technical and business metrics;
- transfer single-writer ownership only after read confidence; keep a time-boxed rollback projection if required;
- remove or disable the replaced OFBiz route/write path/job after the rollback window.

Exit criteria:

- 100% of the selected journey is served by the modern route and services at its SLO;
- users see the tested Modern experience marker;
- the modern service is the documented sole writer for its owned data;
- no modern code reads the legacy database;
- legacy behavior for this slice is disabled, synchronization is stopped in the safe direction, and retirement evidence is recorded;
- a retrospective updates templates, platform, phase gates, and cost assumptions.

### Phase 5 — Repeatable domain waves with concurrent UI migration

**Goal:** scale migration throughput while preserving independent ownership and operational safety.

Run small vertical slices in waves rather than attempting an entire component. A plausible dependency-aware sequence, subject to Phase 0 findings, is:

1. reference/read domains and notifications;
2. Party/contact journeys and product catalog/pricing queries;
3. inventory/facility availability and reservation;
4. order capture and order lifecycle using sagas;
5. invoicing and payments, with stronger audit/compliance gates;
6. fulfilment, manufacturing, work management, HR, marketing, and content based on business priority;
7. ledger and cross-domain reporting after upstream event quality is proven.

For every slice, execute the same lifecycle:

1. discover behavior and dependencies;
2. define contracts, owner, data classification, SLO, and threat model;
3. implement service, data migration, events, UI route/BFF, marker, and observability together;
4. shadow and reconcile;
5. canary reads, then transfer writes;
6. ramp traffic and verify business metrics;
7. remove the old route, service calls, ECAs, scheduled jobs, and permissions;
8. close synchronization and archive/remove obsolete data after retention approval.

Concurrency rules:

- teams may work concurrently only on slices with declared data ownership and versioned contracts;
- an enabling API without its UI may ship dark, but a route is not marked modern until its end-to-end slice is complete;
- UI teams consume BFF/contracts and cannot query shared databases or call the OFBiz adapter directly;
- changes to shared shell/design system/contracts require compatibility tests and named reviewers;
- cross-domain workflows are represented in a saga/event design before implementation;
- platform capacity and on-call load limit work in progress.

Wave exit criteria:

- each migrated route meets the Phase 4 completion definition;
- dependency and ownership maps reflect reality;
- no unresolved permanent dual writes or unbounded synchronization remain;
- service count, cost, latency, incident load, and team cognitive load are reviewed before authorizing the next wave.

### Phase 6 — Legacy contraction and operational hardening

**Goal:** reduce the monolith's footprint and prove the modern platform can operate the business.

Work:

- disable extracted OFBiz components/routes where dependencies allow and remove dead service/entity/ECA/job code;
- replace cross-domain legacy reports with event-fed analytical projections;
- migrate remaining batch jobs to owned Container Apps Jobs with idempotency and run history;
- eliminate remaining shared-database consumers and generic adapter operations;
- run sustained load, chaos/failure, security, penetration, restore, zone outage, and disaster-recovery exercises;
- tune autoscaling, connection pools, Service Bus quotas, PostgreSQL capacity, retention, and cost;
- validate alert coverage, support access, runbooks, audit evidence, incident response, and on-call staffing;
- enforce deprecation windows for old API/event versions and remove expired flags.

Exit criteria:

- OFBiz handles only an explicit, shrinking list of capabilities in the migration ledger;
- the modern platform has passed production-scale restore and recovery exercises against agreed RPO/RTO;
- all remaining legacy dependencies have an owner and retirement date;
- modern operations do not depend on manual database fixes or portal-only configuration.

### Phase 7 — Final cutover and OFBiz retirement

**Goal:** remove the legacy runtime safely and complete ownership transfer.

Work:

- migrate the final routes, APIs, jobs, integrations, admin functions, and historical data access;
- rehearse final cutover and rollback with production-like data and measured duration;
- freeze remaining legacy writes, drain events/synchronization, reconcile invariants, and switch routes;
- retain a time-limited, network-isolated read-only archive for audit/support if policy requires it;
- remove OFBiz from Front Door origins, revoke identities/secrets, stop schedules, and scale deployment to zero;
- archive required source/data/build artifacts and document retrieval procedures;
- destroy unused Azure resources through Terraform after retention and recovery approval;
- remove legacy build paths, libraries, and local-stack profiles from the repository in reviewable changes;
- update user/support documentation; decide whether the Modern experience marker becomes the normal-product badge, changes to a release marker, or is removed in a deliberate UI release.

Exit criteria:

- no production request, job, integration, or report depends on OFBiz;
- data reconciliation, audit, retention, and legal approvals are complete;
- OFBiz credentials and network access are revoked and its infrastructure is removed or formally archived;
- recovery and business-continuity tests run solely on the target architecture;
- migration governance closes with measured outcomes and remaining technical-debt ownership.

## 6. Definition of done for every migrated slice

A capability is not migrated merely because a service exists. It is done only when all of the following are true:

- business owner accepts functional behavior and measurable outcome;
- bounded context, owner, APIs/events, data ownership, and invariants are documented;
- UI journey is in the modern shell, accessible and responsive, with the Modern experience marker;
- Entra authentication and server-side authorization include positive and negative tests;
- service has no direct dependency on the OFBiz database or generic entity API;
- database migrations, backfill, reconciliation, retention, backup, restore, and rollback have been tested;
- outbox/inbox, idempotency, timeout, retry, DLQ, and replay behavior are tested where messaging is used;
- OpenAPI/event compatibility, unit, integration, end-to-end, performance, accessibility, and security tests pass;
- Sonar reports at least 80% coverage on the slice's newly introduced or modified executable code;
- the module is registered in the root Gradle graph; `./gradlew build` builds/tests it and its reports are included in whole-repository Sonar analysis;
- traces, logs, metrics, SLO dashboard, alerts, runbook, and on-call owner exist;
- Terraform and deployment pipeline create and promote everything needed without portal-only steps;
- legacy route, writer, service/ECA/job, permission, and synchronization are removed after the rollback window;
- migration ledger, route manifest, contract catalog, and architecture documentation are updated;
- temporary feature flags and compatibility code have dated removal work.

## 7. Testing and quality strategy

Use a test pyramid adapted to a distributed hybrid system:

- domain unit/property tests for invariants and state transitions;
- component tests with real PostgreSQL containers and local messaging/storage substitutes;
- consumer-driven and provider contract tests for APIs and events;
- migration tests against anonymized production-shaped snapshots, including restart/resume and reconciliation;
- OFBiz adapter characterization tests to detect semantic drift;
- hybrid end-to-end tests across login, modern route marker, legacy fallback, and navigation;
- accessibility tests meeting WCAG 2.2 AA, including marker semantics and keyboard/screen-reader behavior;
- security tests for authorization matrices, tenant isolation, CSRF/CSP, injection, SSRF, dependency/container/IaC issues, and origin bypass;
- load/soak tests for user journeys, event lag, connection limits, autoscaling, and noisy-neighbor behavior;
- resilience tests for unavailable dependencies, duplicate/out-of-order events, poison messages, expired tokens, deployment rollback, and regional/platform failures;
- production synthetic checks for one legacy route during migration and every critical modern journey.

The minimum coverage policy is **80% Sonar coverage on New Code**. “New Code” uses the repository's Sonar new-code definition relative to the target branch or configured reference period and therefore covers both added lines and modified executable lines. Supported JVM and frontend line/branch coverage reports are generated through Gradle and imported before the quality gate is evaluated. The threshold applies to services, BFFs, jobs, adapters, web shell/routes, and shared executable packages.

Generated sources, vendored dependencies, declarative contracts, database migrations, Terraform, and other non-executable configuration may be excluded only through the centrally reviewed Sonar configuration. Exclusions require a documented technical reason and may not be introduced merely to satisfy the threshold. Coverage is a guardrail rather than proof of test quality: tests must still exercise domain invariants, authorization failures, error paths, idempotency, migrations, and integration contracts. Teams should not replace meaningful integration or property tests with low-value assertions written solely to raise the percentage.

CI gates fast tests per change and always requires a whole-repository `./gradlew build sonar` gate and successful Sonar quality-gate result before merge. Path-aware jobs improve feedback time but cannot replace this gate. The full hybrid runtime suite runs on merge/schedule. Staging validates actual Azure identity, networking, Service Bus, PostgreSQL, Key Vault, scaling, and policy behavior. Production releases use immutable artifacts promoted from staging, not rebuilt binaries.

## 8. Terraform organization and controls

Keep reusable modules separate from environment composition. Pin Terraform and provider versions and commit dependency lock files. A representative state split is:

- `foundation`: resource groups, network, DNS, shared monitoring, policy;
- `edge`: Front Door, WAF, APIM, certificates;
- `data-messaging`: PostgreSQL, Service Bus, Storage, Redis;
- `platform`: Container Apps environment, ACR, shared identities;
- one state per service/application deployment where independent lifecycle is valuable.

Use explicit remote-state outputs sparingly; prefer stable resource IDs/configuration contracts to a mesh of Terraform state dependencies. Sensitive outputs remain sensitive and are not passed through CI logs. Environments use separate variable files and subscriptions/resource groups, with policy preventing accidental cross-environment references.

Pull requests show a saved plan and policy/security results. Non-mutating Terraform format, validate, lint, and security checks are exposed through Gradle and participate in `check`/`build`; plan and apply remain separate authorized pipeline operations and are never side effects of `./gradlew build`. Protected apply jobs use federated workload identity, environment approval, concurrency locks, and drift detection. Emergency portal changes follow break-glass controls and must be reconciled into Terraform immediately. Destruction protection applies to production databases, state, Key Vault, and other durable stores; deletion requires a separate approved workflow.

## 9. Key risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Hidden behavior in XML, ECAs, jobs, or scripts | Static inventory plus runtime traces, characterization tests, and slice-specific dependency review. |
| Wrong service boundaries create chatty calls or distributed transactions | Domain workshops, start with coarse bounded contexts, use events/projections, and split only from evidence. |
| Shared database persists under a microservice label | Enforce service credentials/database ownership, architecture tests, migration ledger, and a ban on direct OFBiz DB access. |
| Dual-write divergence | Single-writer cutovers, transactional outbox, resumable backfill, reconciliation, and time-boxed sync. |
| Identity/authorization gaps between modern and legacy routes | Entra bridge, server-side exchange, parity/negative tests, common edge, audit logs, and origin lockdown. |
| UI becomes visually modern while still coupled to legacy | Marker gate requires owned BFF/service/data path and end-to-end tests; no direct adapter calls from UI. |
| Distributed system is harder to operate | Limit initial service count, standard templates, managed Azure services, SLOs, tracing, runbooks, and WIP limits. |
| Local environment diverges from Azure | Narrow provider abstractions, containerized real databases, contract tests, and periodic ephemeral-Azure tests. |
| Terraform blast radius or state loss | State separation, versioned/locked backend, saved plans, approvals, protection, backup, and restore drills. |
| Cost grows with every extracted service | Budgets/tags, per-slice cost estimates, scale-to-zero in non-prod, shared servers where safe, and wave cost reviews. |
| Big-bang UI rewrite stalls business migration | Route-by-route shell composition delivered with each service slice and stable edge URLs. |
| Legacy rollback corrupts modern-owned data | Define rollback before cutover; use forward repair or reverse projection only when tested; never assume route rollback is sufficient. |

## 10. Measures of progress and success

Track outcomes rather than service count:

- percentage of user journeys and production traffic on completed modern routes;
- percentage of business writes owned exclusively by modern services;
- number of legacy routes, jobs, ECAs, database writers, and integrations retired;
- reconciliation defects and synchronization lag per in-flight slice;
- deployment frequency, change lead time, failed-deployment rate, and mean time to restore;
- SLO attainment, p95/p99 latency, incident volume, DLQ age/depth, and recovery-test results;
- security findings, authorization test coverage, secretless workload percentage, and patch latency;
- accessibility conformance and task success/error rates for migrated journeys;
- developer bootstrap time, local smoke-test duration, and CI feedback time;
- Azure cost per environment and per key business transaction;
- temporary flags/adapters/synchronizations past their removal date.

The migration is successful only when improved delivery and operability accompany correct business behavior. A growing number of containers with the monolithic database and UI still in control is not progress toward the target state.

## 11. Immediate next actions

1. Approve this plan's architectural defaults as hypotheses and assign owners for Phase 0 decisions.
2. Generate the component/service/entity/ECA/route dependency inventory from the current repository and validate it with production traces.
3. Hold domain workshops and choose the first vertical slice from evidence.
4. Record ADRs for Entra tenant/user population, Container Apps, Service Bus tier, PostgreSQL tenancy, Front Door/APIM routing, UI composition, and regional RPO/RTO.
5. Create the capability map, migration ledger, route manifest schema, and contract catalog.
6. Implement Phase 1 locally before provisioning production infrastructure.
7. Build Terraform bootstrap and a disposable dev environment, then prove deployment/observability/rollback with a non-business sample.
8. Introduce the edge, identity bridge, and OFBiz adapter only after threat modeling and characterization tests are in place.
9. Deliver the first service and its UI route as one vertical production slice, measure it, and refine the factory before increasing concurrency.
